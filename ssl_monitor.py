#!/usr/bin/env python3
"""
SSL Certificate Monitoring Script

This script monitors HTTPS sites for SSL certificate validity and sends Slack notifications
when certificates are invalid or expiring soon.
"""

import sys
import os
import ssl
import socket
import json
import re
import time
import requests
from datetime import datetime, timedelta
from urllib.parse import urlparse
import logging

# Add dimple_utils to the path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'dimple_utils', 'dimple_utils'))

from logging_utils import setup_logging
from config_utils import load_properties


class SSLCertificateMonitor:
    """SSL Certificate monitoring class"""
    
    def __init__(self, config_path="configs/sites.json", properties_path="configs/default.properties"):
        """Initialize the SSL monitor with configuration"""
        self.url_list = self.load_config(config_path)
        
        # Load properties configuration
        self.config = load_properties(properties_path)
        
        # Setup logging from properties
        log_level = self.config.get('logging.level', 'INFO')
        log_file = self.config.get('logging.file', 'ssl_monitor.log')
        log_directory = self.config.get('logging.directory', 'logs')
        
        # Convert string log level to logging constant
        log_level_map = {
            'DEBUG': logging.DEBUG,
            'INFO': logging.INFO,
            'WARNING': logging.WARNING,
            'ERROR': logging.ERROR,
            'CRITICAL': logging.CRITICAL
        }
        log_level_constant = log_level_map.get(log_level.upper(), logging.INFO)
        
        setup_logging(log_directory, log_level_constant, log_file)
        self.logger = logging.getLogger()
        
        self.warning_days = int(self.config.get('monitoring.warning_days', 7))
        self.timeout = int(self.config.get('monitoring.timeout_seconds', 10))
        
        # Slack configuration
        self.slack_enabled = self.config.get('SLACK_ENABLED', 'false').lower() == 'true'
        self.slack_webhook_url = self.config.get('SLACK_WEBHOOK_URL')
        self.send_weekly_report = self.config.get('SLACK_SEND_WEEKLY_REPORT_ON_SUNDAY', 'false').lower() == 'true'
        self.slack_status_notify_user_id = self.config.get('SLACK_STATUS_NOTIFY_USER_ID')
        
        # Log Slack configuration
        self.logger.info(f"Slack configuration loaded:")
        self.logger.info(f"  - Slack enabled: {self.slack_enabled}")
        self.logger.info(f"  - Webhook URL configured: {'Yes' if self.slack_webhook_url else 'No'}")
        self.logger.info(f"  - Weekly report enabled: {self.send_weekly_report}")
        self.logger.info(f"  - User ID configured: {'Yes' if self.slack_status_notify_user_id else 'No'}")
        if self.slack_status_notify_user_id:
            self.logger.info(f"  - User ID: {self.slack_status_notify_user_id}")
        
    def load_config(self, config_path):
        """Load configuration from JSON file"""
        try:
            with open(config_path, 'r') as f:
                return json.load(f)
        except FileNotFoundError:
            self.logger.error(f"Configuration file not found: {config_path}")
            raise
        except json.JSONDecodeError as e:
            self.logger.error(f"Invalid JSON in configuration file: {e}")
            raise
        
    def get_ssl_certificate_info(self, hostname, port=443):
        """Get SSL certificate information for a hostname"""
        self.logger.debug(f"Attempting SSL connection to {hostname}:{port}")
        
        try:
            # Create SSL context
            context = ssl.create_default_context()
            context.check_hostname = False
            context.verify_mode = ssl.CERT_REQUIRED  # Changed to get certificate data
            
            self.logger.debug(f"Created SSL context for {hostname}")
            
            # Connect to the host
            self.logger.debug(f"Creating socket connection to {hostname}:{port}")
            with socket.create_connection((hostname, port), timeout=self.timeout) as sock:
                self.logger.debug(f"Socket connected to {hostname}:{port}")
                
                with context.wrap_socket(sock, server_hostname=hostname) as ssock:
                    self.logger.debug(f"SSL socket wrapped for {hostname}")
                    cert = ssock.getpeercert()
                    self.logger.debug(f"Retrieved certificate for {hostname}: {cert}")
                    
            return {
                'success': True,
                'cert': cert,
                'error': None
            }
        except Exception as e:
            self.logger.error(f"SSL connection failed for {hostname}: {e}")
            return {
                'success': False,
                'cert': None,
                'error': str(e)
            }
    
    def parse_certificate_dates(self, cert):
        """Parse certificate dates and return expiration info"""
        if not cert:
            self.logger.error("Certificate data is None or empty")
            return None
        
        # Debug: Log the entire certificate structure
        self.logger.debug(f"Certificate data: {cert}")
        
        # Parse the notAfter date
        not_after_str = cert.get('notAfter')
        if not_after_str:
            self.logger.debug(f"Certificate notAfter string: '{not_after_str}'")
            
            # Try multiple date formats
            date_formats = [
                '%b %d %H:%M:%S %Y %Z',  # Dec 31 23:59:59 2023 GMT
                '%b %d %H:%M:%S %Y',     # Dec 31 23:59:59 2023
                '%Y-%m-%d %H:%M:%S',     # 2023-12-31 23:59:59
                '%Y-%m-%d',              # 2023-12-31
                '%d/%m/%Y',              # 31/12/2023
                '%m/%d/%Y',              # 12/31/2023
            ]
            
            for date_format in date_formats:
                try:
                    not_after = datetime.strptime(not_after_str, date_format)
                    now = datetime.now()
                    days_until_expiry = (not_after - now).days
                    
                    self.logger.debug(f"Successfully parsed date with format '{date_format}': {not_after}")
                    
                    return {
                        'expiry_date': not_after,
                        'days_until_expiry': days_until_expiry,
                        'is_expired': days_until_expiry < 0,
                        'is_expiring_soon': days_until_expiry <= self.warning_days
                    }
                except ValueError:
                    continue
            
            # If none of the formats worked, log the actual string and try a more flexible approach
            self.logger.error(f"Could not parse certificate date with any known format: '{not_after_str}'")
            
            # Try to extract just the year, month, day if it's in a different format
            try:
                # Look for patterns like "Dec 31 2023" or "31 Dec 2023"
                date_match = re.search(r'(\w{3})\s+(\d{1,2})\s+(\d{4})', not_after_str)
                if date_match:
                    month_str, day_str, year_str = date_match.groups()
                    month_map = {
                        'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
                        'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12
                    }
                    if month_str in month_map:
                        not_after = datetime(int(year_str), month_map[month_str], int(day_str))
                        now = datetime.now()
                        days_until_expiry = (not_after - now).days
                        
                        return {
                            'expiry_date': not_after,
                            'days_until_expiry': days_until_expiry,
                            'is_expired': days_until_expiry < 0,
                            'is_expiring_soon': days_until_expiry <= self.warning_days
                        }
            except Exception as e:
                self.logger.error(f"Error in flexible date parsing: {e}")
            
            return None
        else:
            self.logger.error("Certificate does not contain 'notAfter' field")
            # Log all available fields in the certificate
            self.logger.debug(f"Available certificate fields: {list(cert.keys())}")
            return None
        
        return None
    
    def send_slack_message(self, message: str, user_id: str = None) -> bool:
        """
        Send a simple message to Slack using webhook
        
        :param message: Message to send
        :param user_id: Optional user ID to mention
        :return: True if successful, False otherwise
        """
        if not self.slack_enabled or not self.slack_webhook_url:
            self.logger.debug("Slack not enabled or webhook URL not configured")
            return False
        
        try:
            # Use provided user_id, or fall back to configured slack_status_notify_user_id
            target_user_id = user_id if user_id else self.slack_status_notify_user_id
            
            payload = {
                "text": message,
                "channel": target_user_id if target_user_id else None
            }
            
            # Remove None values from payload
            payload = {k: v for k, v in payload.items() if v is not None}
            
            self.logger.info(f"Sending Slack message with payload: {json.dumps(payload, indent=2)}")
            
            response = requests.post(
                self.slack_webhook_url,
                json=payload,
                headers={'Content-type': 'application/json'},
                timeout=10
            )
            
            if response.status_code == 200:
                self.logger.info("Slack message sent successfully")
                return True
            else:
                self.logger.error(f"Failed to send Slack message: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            self.logger.error(f"Error sending Slack message: {e}")
            return False
    
    def send_ssl_certificate_alert(self, site_name: str, site_url: str, status: str, 
                                 message: str, expiry_date: str = None, 
                                 days_until_expiry: int = None) -> bool:
        """
        Send a formatted SSL certificate alert to Slack
        
        :param site_name: Name of the site
        :param site_url: URL of the site
        :param status: Certificate status (valid, expiring_soon, expired, error)
        :param message: Status message
        :param expiry_date: Certificate expiry date (optional)
        :param days_until_expiry: Days until expiry (optional)
        :return: True if successful, False otherwise
        """
        # Determine emoji based on status
        status_config = {
            'valid': '✅',
            'expiring_soon': '⚠️',
            'expired': '🚨',
            'error': '❌'
        }
        
        emoji = status_config.get(status, 'ℹ️')
        
        # Build the message
        alert_message = f"{emoji} SSL Certificate Alert for {site_name}\n"
        alert_message += f"URL: {site_url}\n"
        alert_message += f"Status: {message}\n"
        
        if expiry_date:
            alert_message += f"Expiry Date: {expiry_date}\n"
        
        if days_until_expiry is not None:
            alert_message += f"Days Until Expiry: {days_until_expiry}"
        
        # Send message with user mention if configured
        return self.send_slack_message(alert_message, self.slack_status_notify_user_id)
    
    def send_summary_report(self, results: list) -> bool:
        """
        Send a summary report of SSL monitoring results to Slack
        
        :param results: List of monitoring results for all sites
        :return: True if successful, False otherwise
        """
        # Calculate summary statistics
        total_sites = len(results)
        valid_sites = len([r for r in results if r['status'] == 'valid'])
        expired_sites = len([r for r in results if r['status'] == 'expired'])
        expiring_sites = len([r for r in results if r['status'] == 'expiring_soon'])
        error_sites = len([r for r in results if r['status'] == 'error'])
        
        # Determine overall status and emoji
        if expired_sites > 0 or error_sites > 0:
            emoji = "🚨"
            status = "Issues Found"
        elif expiring_sites > 0:
            emoji = "⚠️"
            status = "Warnings"
        else:
            emoji = "✅"
            status = "All Good"
        
        # Build summary message
        summary_message = f"{emoji} SSL Monitoring Summary - {status}\n"
        summary_message += f"Total Sites: {total_sites}\n"
        summary_message += f"Valid: {valid_sites}\n"
        summary_message += f"Expired: {expired_sites}\n"
        summary_message += f"Expiring Soon: {expiring_sites}\n"
        summary_message += f"Errors: {error_sites}\n\n"
        
        # Add site details
        summary_message += "Sites Checked:\n"
        for result in results:
            site_name = result['site_name']
            site_url = result['site_url']
            site_status = result['status']
            
            # Add appropriate emoji for each site
            if site_status == 'valid':
                site_emoji = "✅"
            elif site_status == 'expiring_soon':
                site_emoji = "⚠️"
            elif site_status == 'expired':
                site_emoji = "🚨"
            else:  # error
                site_emoji = "❌"
            
            # Build site line with expiry information
            site_line = f"{site_emoji} {site_name} ({site_url})"
            
            # Add expiry date if available
            if 'expiry_date' in result and result['expiry_date']:
                expiry_date = result['expiry_date']
                if isinstance(expiry_date, str):
                    # If it's already a string (ISO format), use it directly
                    site_line += f" - Expires: {expiry_date}"
                else:
                    # If it's a datetime object, format it
                    site_line += f" - Expires: {expiry_date.strftime('%Y-%m-%d')}"
            
            # Add days until expiry if available
            if 'days_until_expiry' in result and result['days_until_expiry'] is not None:
                days = result['days_until_expiry']
                if days < 0:
                    site_line += f" (Expired {abs(days)} days ago)"
                elif days == 0:
                    site_line += " (Expires today)"
                else:
                    site_line += f" ({days} days remaining)"
            
            summary_message += site_line + "\n"
        
        return self.send_slack_message(summary_message)
    
    def check_site_ssl(self, site_config):
        """Check SSL certificate for a single site"""
        site_name = site_config['name']
        site_url = site_config['url']
        
        self.logger.info(f"Checking SSL certificate for {site_name} ({site_url})")
        
        # Parse URL to get hostname
        parsed_url = urlparse(site_url)
        hostname = parsed_url.hostname
        
        if not hostname:
            self.logger.error(f"Invalid URL for {site_name}: {site_url}")
            return {
                'site_name': site_name,
                'site_url': site_url,
                'status': 'error',
                'message': 'Invalid URL',
                'should_notify': True
            }
        
        # Get SSL certificate info
        cert_info = self.get_ssl_certificate_info(hostname)
        
        if not cert_info['success']:
            self.logger.error(f"Failed to get SSL certificate for {site_name}: {cert_info['error']}")
            
            # Check if it's an expired certificate error
            error_message = cert_info['error']
            if 'certificate has expired' in error_message.lower():
                enhanced_message = f"SSL connection failed: Certificate has expired (unable to retrieve expiry date due to SSL verification failure)"
            else:
                enhanced_message = f"SSL connection failed: {error_message}"
            
            return {
                'site_name': site_name,
                'site_url': site_url,
                'status': 'error',
                'message': enhanced_message,
                'should_notify': True
            }
        
        # Parse certificate dates
        expiry_info = self.parse_certificate_dates(cert_info['cert'])
        
        if not expiry_info:
            self.logger.error(f"Could not parse certificate dates for {site_name}")
            return {
                'site_name': site_name,
                'site_url': site_url,
                'status': 'error',
                'message': 'Could not parse certificate dates',
                'should_notify': True
            }
        
        # Determine status and notification requirements
        if expiry_info['is_expired']:
            status = 'expired'
            message = f"Certificate expired {abs(expiry_info['days_until_expiry'])} days ago"
            should_notify = True
        elif expiry_info['is_expiring_soon']:
            status = 'expiring_soon'
            message = f"Certificate expires in {expiry_info['days_until_expiry']} days"
            should_notify = True
        else:
            status = 'valid'
            message = f"Certificate is valid for {expiry_info['days_until_expiry']} more days"
            should_notify = False
        
        self.logger.info(f"{site_name}: {message}")
        
        return {
            'site_name': site_name,
            'site_url': site_url,
            'status': status,
            'message': message,
            'expiry_date': expiry_info['expiry_date'].isoformat(),
            'days_until_expiry': expiry_info['days_until_expiry'],
            'should_notify': should_notify
        }
    
    def log_certificate_status(self, result):
        """Log certificate status for a site result"""
        site_name = result['site_name']
        site_url = result['site_url']
        status = result['status']
        message = result['message']
        
        # Log with appropriate level based on status
        if status == 'expired':
            self.logger.error(f"🚨 EXPIRED: {site_name} ({site_url}) - {message}")
        elif status == 'expiring_soon':
            self.logger.warning(f"⚠️  EXPIRING SOON: {site_name} ({site_url}) - {message}")
        elif status == 'error':
            self.logger.error(f"❌ ERROR: {site_name} ({site_url}) - {message}")
        else:
            self.logger.info(f"✅ VALID: {site_name} ({site_url}) - {message}")
    
    def monitor_all_sites(self):
        """Monitor all configured sites"""
        sites = self.url_list.get('sites', [])
        enabled_sites = [site for site in sites if site.get('enabled', True)]
        
        self.logger.info(f"Starting SSL monitoring for {len(enabled_sites)} sites")
        
        results = []
        issues_found = 0
        
        for site in enabled_sites:
            try:
                result = self.check_site_ssl(site)
                results.append(result)
                
                # Log the result
                self.log_certificate_status(result)
                
                # Count issues
                if result['should_notify']:
                    issues_found += 1
                    
                    # Send Slack notification for critical issues
                    if self.slack_enabled and result['status'] in ['expired', 'expiring_soon', 'error']:
                        self.send_ssl_certificate_alert(
                            site_name=result['site_name'],
                            site_url=result['site_url'],
                            status=result['status'],
                            message=result['message'],
                            expiry_date=result.get('expiry_date'),
                            days_until_expiry=result.get('days_until_expiry')
                        )
                    
            except Exception as e:
                self.logger.error(f"Error monitoring {site['name']}: {e}")
                error_result = {
                    'site_name': site['name'],
                    'site_url': site['url'],
                    'status': 'error',
                    'message': f"Monitoring error: {str(e)}",
                    'should_notify': True
                }
                results.append(error_result)
                self.log_certificate_status(error_result)
                issues_found += 1
                
                # Send Slack notification for errors
                if self.slack_enabled:
                    self.send_ssl_certificate_alert(
                        site_name=error_result['site_name'],
                        site_url=error_result['site_url'],
                        status='error',
                        message=error_result['message']
                    )
        
        # Log summary
        total_sites = len(enabled_sites)
        valid_sites = len([r for r in results if r['status'] == 'valid'])
        expired_sites = len([r for r in results if r['status'] == 'expired'])
        expiring_sites = len([r for r in results if r['status'] == 'expiring_soon'])
        error_sites = len([r for r in results if r['status'] == 'error'])
        
        self.logger.info(f"Monitoring complete: {total_sites} sites checked, "
                        f"{valid_sites} valid, {expired_sites} expired, "
                        f"{expiring_sites} expiring soon, {error_sites} errors, "
                        f"{issues_found} issues found")
        
        # Send weekly report on Sundays if enabled
        if self.slack_enabled and self.send_weekly_report:
            current_day = datetime.now().weekday()  # 0 = Monday, 6 = Sunday
            current_day_name = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][current_day]
            
            self.logger.info(f"Today is {current_day_name} (day {current_day})")
            self.logger.info(f"Weekly report enabled: {self.send_weekly_report}")
            
            if current_day == 6:  # Sunday
                self.logger.info("Sending weekly summary report to Slack (Sunday)")
                self.send_summary_report(results)
            else:
                self.logger.info(f"Weekly report not sent - today is {current_day_name}, not Sunday")
        else:
            if not self.slack_enabled:
                self.logger.info("Weekly report not sent - Slack notifications disabled")
            if not self.send_weekly_report:
                self.logger.info("Weekly report not sent - weekly reporting disabled")
        
        return results


def main():
    """Main function"""
    try:
        monitor = SSLCertificateMonitor()
        results = monitor.monitor_all_sites()
        
        # Exit with error code if any critical issues found
        critical_issues = [r for r in results if r['status'] in ['expired', 'error']]
        if critical_issues:
            sys.exit(2)
        else:
            sys.exit(0)
            
    except Exception as e:
        print(f"Fatal error in SSL monitoring: {e}")
        
        # Try to send fatal error notification to Slack
        try:
            monitor = SSLCertificateMonitor()
            if monitor.slack_enabled:
                fatal_message = f"🚨 FATAL ERROR in SSL Certificate Monitoring\n\nError: {str(e)}\n\nPlease check the monitoring system immediately."
                monitor.send_slack_message(fatal_message, monitor.slack_status_notify_user_id)
                print("Fatal error notification sent to Slack")
        except Exception as slack_error:
            print(f"Failed to send Slack notification for fatal error: {slack_error}")
        
        sys.exit(1)


if __name__ == "__main__":
    main()
