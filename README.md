# Tasks

Tasks is a simple and versatile asynchronous task handler.

It can be used to create complex task systems, and supports custom
executor services and progress objects.


### Creating Tasks
Creating a task with Tasks is easy:
```java
int max = 100000;
Task<Integer, String> task = Task.of((Task<Integer, String>.Delegate delegate) -> {
    for (int i = 0; i <= max; i++) {
        delegate.setProgress(i);
        LockSupport.parkNanos((long) 1E+8); // 100ms
    }
    return "Hello, world!";
});

task.run(); // or task.runAsync() or task.runAsync(Executor)
```
This snippet creates a new Task object that returns
Integer progress objects and resolves to a String object.
When we run the task, we can access the Integer value it's on
because it calls `delegate.setProgress(i)` in its for-loop. When it completes, its getResult method will return "Hello, world!"

Tasks also have several methods for easily tracking progress and results:
- `whileRunning(Consumer<P> progressConsumer, Duration period)` causes the progress consumer to be run every _period_, with the latest progress as input.
- `onProgress(BiConsumer<P, P> progressConsumer)` works similarly to `whileRunning`, except runs every time `setProgress` is called from inside the task, with the old progress and new progress as input respectively.
- `onResult(Consumer<R> resultConsumer)` is run once when the task completes, with the result as input.

Two of these methods are shown here:
```java
Task.of((Task<Integer, String>.Delegate taskDelegate) -> {
    for (int i = 0; i <= max; i++) {
        taskDelegate.setProgress(i);
        LockSupport.parkNanos((long) 1E+8);
    }
    return "Hello, world!";
})
    .whileRunning((progress) -> System.out.print("\r" + getProgressBar(progress, max)), Duration.ofMillis(1000))
    .onResult((result) -> System.out.println("\n" + result))
    .runAsync().join(); // equivalent to run();
```

## Using Tasks in your Maven project

Tasks is deployed directly to GitHub! This makes it nice and easy to use it, without having to use messy software like JitPack or publish to an actual repository!

Simply add this to your `pom.xml`:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.2.4</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <createDependencyReducedPom>false</createDependencyReducedPom>
                    </configuration>
                </execution>
            </executions>
            <configuration>
                <relocations>
                    <relocation>
                        <pattern>io.github.bluelhf.tasks</pattern>
                        <shadedPattern>YOUR.PACKAGE.HERE.tasks</shadedPattern>
                    </relocation>
                </relocations>
            </configuration>
        </plugin>
    </plugins>
</build>

<repositories>
    <repository>
        <id>tasks-repo</id>
        <url>https://raw.githubusercontent.com/bluelhf/Tasks/mvn-repo/</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.github.bluelhf</groupId>
        <artifactId>Tasks</artifactId>
        <version>0000</version>
    </dependency>
</dependencies>
```

(Make sure to replace YOUR.PACKAGE.HERE with your qualified artifact ID!)