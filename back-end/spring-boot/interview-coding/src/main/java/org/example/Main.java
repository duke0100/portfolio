package org.example;

import lombok.extern.slf4j.Slf4j;
import org.example.concurrency.Concurrency;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
@Slf4j
public class Main {
    static void main() throws InterruptedException {
        runUnsafe();
    }

    private static void runUnsafe() throws InterruptedException {
        Concurrency example = new Concurrency();

        Thread writer = new Thread(() -> example.writerSafe());

        Thread reader = new Thread(() -> {
            // busy-wait until flag becomes true
            while (!example.isFlagSafe()) {
                // spin
            }
            System.out.println("Reader saw value = " + example.getValueSafe());
        });

        reader.start();
        Thread.sleep(100); // give reader a head start into the spin loop
        writer.start();

        writer.join(2000);
        reader.join(2000);
        if(reader.isAlive()){
            log.error("Bug detected");
        }
    }
}
