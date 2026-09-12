package concurrency;

import org.example.concurrency.Concurrency;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrencyTest {
    @Test
    void unsafeVersion_readerMayHangOrSeeStaleValue() throws InterruptedException {
        Concurrency example = new Concurrency();

        Thread writer = new Thread(example::writerUnsafe);

        final int[] observedValue = {-1};

        Thread reader = new Thread(() -> {
            while (!example.isFlagUnsafe()) {
                // spin
            }
            observedValue[0] = example.getValueUnsafe();
        });

        reader.setDaemon(true);
        reader.start();
        Thread.sleep(100);
        writer.start();

        writer.join(2000);
        reader.join(2000);

        if (reader.isAlive()) {
            System.out.println("BUG DETECTED: reader never observed flag flip -> visibility issue confirmed");
            return;
        }

        System.out.println("Reader saw value = " + observedValue[0]);
        if (observedValue[0] != 42) {
            System.out.println("BUG DETECTED: reader saw stale value = " + observedValue[0]);
        }
    }

    @Test
    void testUnsafe_readerMayHangOrSawStaleValue() throws InterruptedException {
        Concurrency concurrency = new Concurrency();
        AtomicInteger finalValue = new AtomicInteger(10);
        Thread writer = new Thread(concurrency::writerUnsafe);
        Thread reader = new Thread(() -> {
            while(!concurrency.isFlagUnsafe()){
            }
            finalValue.set(concurrency.getValueUnsafe());
        });

        reader.setDaemon(true);
        reader.start();
        Thread.sleep(1000);
        writer.start();

        reader.join(2000);
        writer.join(2000);
        if(reader.isAlive()){
            System.out.println("Bug detected here, reader still keep running when flag equals true");
        }
        System.out.println("final value: " + finalValue.get());
    }
}
