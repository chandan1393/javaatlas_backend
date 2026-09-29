package com.javaatlas.user;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.javaatlas.AppProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends account emails over SMTP (Brevo, Gmail, Amazon SES, Zoho, …).
 * If email isn't configured, a development setup logs the reset link instead.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senders;
    private final AppProperties props;

    public MailService(ObjectProvider<JavaMailSender> senders, AppProperties props) {
        this.senders = senders;
        this.props = props;
    }

    public void sendPasswordReset(String to, String name, String link, Duration validFor) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender instanceof JavaMailSenderImpl impl && !AppProperties.has(impl.getHost())) {
            sender = null;   // SPRING_MAIL_HOST was passed but left empty
        }
        if (sender == null || !AppProperties.has(props.mailFrom())) {
            if (props.cookieSecure()) {
                log.error("Password reset requested for {}, but email isn't configured. Set SPRING_MAIL_HOST and MAIL_FROM.", to);
            } else {
                log.warn("Email isn't configured, so here is the password reset link for {} (development only): {}", to, link);
            }
            return;
        }
        long minutes = validFor.toMinutes();
        String firstName = name == null ? "there" : name.trim().split("\\s+")[0];
        String text = """
                Hi %s,

                Someone asked to reset the password for your JavaAtlas account.
                Open this link to choose a new password. It works for %d minutes and only once:

                %s

                If you didn't ask for this, you can ignore this email. Your password stays the same.
                """.formatted(firstName, minutes, link);
        String safeLink = HtmlUtils.htmlEscape(link);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#101828">
                  <h2 style="margin:0 0 12px;font-size:20px">Reset your password</h2>
                  <p style="margin:0 0 16px;line-height:1.5">Hi %s, someone asked to reset the password for your JavaAtlas account.
                  This link works for %d minutes and only once.</p>
                  <p style="margin:0 0 24px"><a href="%s" style="display:inline-block;background:#6C4DFF;color:#ffffff;text-decoration:none;
                  padding:12px 20px;border-radius:10px;font-weight:bold">Choose a new password</a></p>
                  <p style="margin:0 0 8px;font-size:13px;color:#667085">Or paste this address into your browser:<br>%s</p>
                  <p style="margin:16px 0 0;font-size:13px;color:#667085">If you didn't ask for this, ignore this email. Your password stays the same.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(firstName), minutes, safeLink, safeLink);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.mailFrom());
            helper.setTo(to);
            helper.setSubject("Reset your JavaAtlas password");
            helper.setText(text, html);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("Could not send the password reset email to {}", to, e);
        }
    }

    /** True when SMTP and MAIL_FROM are configured. */
    public boolean enabled() {
        return sender() != null;
    }

    /**
     * Sends a plain-text email (used for owner notifications). Plain text on purpose: content typed by visitors
     * can't inject HTML. Returns false if email isn't configured or sending failed.
     */
    public boolean sendPlain(String to, String subject, String text, String replyTo) {
        JavaMailSender sender = sender();
        if (sender == null || !AppProperties.has(to)) return false;
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(props.mailFrom());
            helper.setTo(to);
            if (AppProperties.has(replyTo)) helper.setReplyTo(replyTo);
            helper.setSubject(subject.replaceAll("[\\r\\n]+", " "));
            helper.setText(text, false);
            sender.send(message);
            return true;
        } catch (MessagingException | MailException e) {
            log.error("Could not send an email to {}", to, e);
            return false;
        }
    }

    private JavaMailSender sender() {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender instanceof JavaMailSenderImpl impl && !AppProperties.has(impl.getHost())) return null;
        return sender != null && AppProperties.has(props.mailFrom()) ? sender : null;
    }
}
