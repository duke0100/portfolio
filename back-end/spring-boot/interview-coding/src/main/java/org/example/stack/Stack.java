package org.example.stack;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
public class Stack<T extends Comparable<T>> {
    private final Deque<T> stack = new ArrayDeque<>();

    public void push(T value){
        stack.push(value);
    }

    public T pop(){
        validateStackIsEmpty();
        return stack.pop();
    }

    public T peek(){
        validateStackIsEmpty();
        return stack.peek();
    }

    public T getMin(){
        validateStackIsEmpty();
        T min = null;
        for (T item : stack){
            if (min == null || item.compareTo(min) < 0 ) min = item;
        }
        return min;
    }

    public T getMax(){
        validateStackIsEmpty();
        T max = null;
        for (T item : stack){
            if (max == null || item.compareTo(max) > 0 ) max = item;
        }
        return max;
    }

    public T getMostFrequent(){
        validateStackIsEmpty();
        T result = null;
        int maxCount = 0;
        Map<T, Integer> freq = new HashMap<>();
        Iterator<T> iterator = stack.descendingIterator();
        while(iterator.hasNext()){
            T item = iterator.next();
            int currentCount = freq.merge(item, 1, Integer::sum);
            if(currentCount >= maxCount){
                result = item;
                maxCount = currentCount;
            }
        }
        return result;
    }

    //=================Optimize====================
    private final Deque<T> minStack = new ArrayDeque<>();
    private final Deque<T> maxStack = new ArrayDeque<>();

    public void pushV2(T value){
        stack.push(value);
        if(minStack.isEmpty() || value.compareTo(minStack.peek()) < 0) {
            minStack.push(value);
        } else {
            minStack.push(minStack.peek());
        }

        if(maxStack.isEmpty() || value.compareTo(maxStack.peek()) > 0) {
            maxStack.push(value);
        } else {
            maxStack.push(maxStack.peek());
        }
    }

    public T popV2(){
        validateStackIsEmpty();
        minStack.pop();
        maxStack.pop();
        return stack.pop();
    }

    public T peekV2(){
        validateStackIsEmpty();
        return stack.peek();
    }

    public T getMinV2(){
        validateStackIsEmpty();
        return minStack.peek();
    }

    public T getMaxV2(){
        validateStackIsEmpty();
        return maxStack.peek();
    }

    private void validateStackIsEmpty(){
        if(stack.isEmpty()) {
            throw new NoSuchElementException("Stack is empty");
        }
    }

    public void clear(){
        stack.clear();
        Deque<Integer> deque = new LinkedList<>();
    }
}
