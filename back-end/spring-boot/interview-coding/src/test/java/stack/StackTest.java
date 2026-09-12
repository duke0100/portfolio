package stack;

import org.example.stack.Stack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StackTest {
    @Test
    void testStack_basicMethod(){
        Stack<Integer> stack = new Stack<>();
        //push: 1,3,7,5
        stack.push(1);
        stack.push(1);
        stack.push(3);
        stack.push(7);
        stack.push(5);
        stack.push(3);
        stack.push(5);

        assertEquals(5, stack.peek());
        assertEquals(5, stack.pop());
        assertEquals(1, stack.getMin());
        assertEquals(7, stack.getMax());
        assertEquals(3, stack.getMostFrequent());

        stack.clear();

        stack.pushV2(1);
        stack.pushV2(1);
        stack.pushV2(3);
        stack.pushV2(7);
        stack.pushV2(5);
        stack.pushV2(3);
        stack.pushV2(5);
        assertEquals(5, stack.peekV2());
        assertEquals(5, stack.popV2());
        assertEquals(1, stack.getMinV2());
        assertEquals(7, stack.getMaxV2());
    }
}
