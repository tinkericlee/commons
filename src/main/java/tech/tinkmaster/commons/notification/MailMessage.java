package tech.tinkmaster.commons.notification;

import lombok.Data;

@Data
public class MailMessage {
  Long id;
  String mailTye;
  String content;
}
