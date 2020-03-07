package tech.tinkmaster.commons.notification;

public interface MailConsumer {
  boolean receiveMessage(MailMessage mailMessage);

  boolean reportMessageConsumedSuccessfully();

  String getMailType();
}
