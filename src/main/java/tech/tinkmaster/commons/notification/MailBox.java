package tech.tinkmaster.commons.notification;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is the message storage containing messages provided by its producer and allow its
 * consumers to consume the unconsumed messages. In this version, mailbox only contains the messages
 * to be consumed so we should provide the serialize way and deserialize method and keep its state
 * to make sure we can dispose the unconsumed messages rather than just discard them.
 */
public abstract class MailBox {

  Logger LOG = LoggerFactory.getLogger(this.getClass());

  /** Key: mailType */
  protected Map<String, MailConsumer> mailConsumers;

  protected Queue<MailMessage> mailMessages;
  private MailBoxNotifier notifyThread;

  private ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

  public MailBox() {
    loadUnconsumedMessages();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  LOG.info("Detect termination message.");
                  saveUnconsumedMessages();
                }));
    mailConsumers = new HashMap<>();
    mailMessages = new ConcurrentLinkedDeque<>();
    notifyThread = new MailBoxNotifier(mailConsumers, mailMessages);
    executorService.submit(notifyThread);
  }

  public void putMailMessage(MailMessage mailMessage) {
    mailMessages.offer(mailMessage);
  }

  public void register(MailConsumer consumer) {
    mailConsumers.put(consumer.getMailType(), consumer);
  }

  /**
   * This class is used for loading unconsumed messages from other data storage. You should append
   * the messages into {@link MailBox#mailMessages} object.
   */
  abstract void loadUnconsumedMessages();

  /**
   * This class is used for saving unconsumed messages from other data storage. Before terminating
   * the jvm, we should store these messages rather than losing them.
   */
  abstract void saveUnconsumedMessages();

  private static class MailBoxNotifier extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(MailBoxNotifier.class);

    protected Map<String, MailConsumer> mailConsumers;

    protected Queue<MailMessage> mailMessages;

    MailBoxNotifier(Map<String, MailConsumer> mailConsumers, Queue<MailMessage> mailMessages) {
      requireNonNull(mailConsumers, "mailConsumers");
      requireNonNull(mailMessages, "mailMessages");
      this.mailConsumers = mailConsumers;
      this.mailMessages = mailMessages;
    }

    @Override
    public void run() {
      while (true) {
        MailMessage mailMessage = mailMessages.poll();
        if (mailMessage != null) {
          MailConsumer consumer = mailConsumers.get(mailMessage.mailTye);
          if (consumer != null) {
            try {
              consumer.receiveMessage(mailMessage);
            } catch (Exception e) {
              LOG.error("Consume message error!", e);
            }
          } else {
            try {
              Thread.sleep(2_000);
            } catch (InterruptedException e) {
              // ignore, sleep silently
            }
          }
        }
      }
    }
  }
}
