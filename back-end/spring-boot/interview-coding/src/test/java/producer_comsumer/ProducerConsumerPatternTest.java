package producer_comsumer;

import org.example.producer_comsumer.ProducerConsumerPattern;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProducerConsumerPatternTest {
    @Test
    void testProducerConsumer_withWaitAndNotify() throws InterruptedException {
        ProducerConsumerPattern pattern = new ProducerConsumerPattern(100);
        int totalItems = 1000;
        CountDownLatch latch = new CountDownLatch(totalItems);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < totalItems; i++) {
                    pattern.produce(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    pattern.consume();
                    latch.countDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();

        boolean finishedInTime = latch.await(5, TimeUnit.SECONDS);

        producer.interrupt();
        consumer.interrupt();
        producer.join(500);
        consumer.join(500);

        assertTrue(finishedInTime, "Consumer phải tiêu thụ đủ " + totalItems + " item");
    }
}
