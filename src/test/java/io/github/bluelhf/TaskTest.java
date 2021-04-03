package io.github.bluelhf;

import io.github.bluelhf.tasks.Task;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

public class TaskTest {
    public static void main(String[] args) {

        int max = 100;
        System.out.println("Counting to " + max + " very slowly...");
        Task.of((Task<Integer, String>.Delegate taskDelegate) -> {
            for (int i = 0; i <= max; i++) {
                taskDelegate.setProgress(i);
                LockSupport.parkNanos((long) 1E+8);
            }
            return "Hello, world!";
        })
                .whileRunning((progress) -> System.out.print("\r" + getProgressBar(progress, max)), Duration.ofMillis(1000))
                .onResult((result) -> System.out.println("\n" + result))
                .runAsync().join();

    }

    private static String getProgressBar(int progress, int max) {
        StringBuilder builder = new StringBuilder();

        int percent = (int) Math.floor(progress / (double) max * 100);
        String prefixFormat = (percent >= 1 ? "=[" : " [") + "%s%% (%s/%s)]";
        String prefix = String.format(prefixFormat, percent, progress, max);

        builder.append("|").append(prefix);
        for (int i = prefix.length(); i <= percent; i++) {
            builder.append("=");
        }
        for (int i = builder.length(); i < 100; i++) {
            builder.append(" ");
        }
        builder.append("|");
        return builder.toString();
    }
}
