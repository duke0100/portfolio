package org.example.producer_comsumer;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class ProducerConsumerPattern {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int capacity;


    public ProducerConsumerPattern(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void produce(int value) throws InterruptedException {
        while(this.queue.size() == this.capacity) {
            log.info("Produce | Queue is full");
            this.wait();
        }
        this.queue.add(value);
        log.info("Produce: {}", value);
        this.notifyAll();
    }

    public synchronized int consume() throws InterruptedException {
        while(this.queue.isEmpty()){
            log.info("Consume | Queue is empty");
            this.wait();
        }
        int result = this.queue.poll();
        log.info("consume: {}", result);
        this.notifyAll();
        return result;
    }

    //==================================
    // this function used to test, not implement anything about this pattern
    BlockingQueue<Integer> blockingQueue = new ArrayBlockingQueue<>(5);
    public void useBlockingQueue(){
        Runnable producer = () -> {
            int value = 0;
            try {
                while (true) {
                    blockingQueue.put(value); // tự block nếu đầy
                    System.out.println("Produced: " + value);
                    value++;
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable consumer = () -> {
            try {
                while (true) {
                    int value = blockingQueue.take(); // tự block nếu rỗng
                    System.out.println("Consumed: " + value);
                    Thread.sleep(150);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        new Thread(producer).start();
        new Thread(consumer).start();
    }
}
