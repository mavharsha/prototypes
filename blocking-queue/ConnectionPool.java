import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConnectionPool {

    private final BlockingQueue<PooledConnection> pool;
    private final int poolSize;

    public ConnectionPool(int poolSize) {
        this.poolSize = poolSize;
        this.pool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.offer(new PooledConnection("conn-" + i));
        }
        System.out.println("Pool initialized with " + poolSize + " connections.");
    }

    public PooledConnection borrow() throws InterruptedException {
        System.out.println(Thread.currentThread().getName() + " waiting for connection...");
        PooledConnection conn = pool.take(); // blocks if empty
        System.out.println(Thread.currentThread().getName() + " acquired " + conn.getName());
        return conn;
    }

    public PooledConnection borrow(long timeout, TimeUnit unit) throws InterruptedException {
        PooledConnection conn = pool.poll(timeout, unit);
        if (conn == null) {
            System.out.println(Thread.currentThread().getName() + " timed out.");
        }
        return conn;
    }

    public void release(PooledConnection conn) {
        if (conn != null) {
            pool.offer(conn);
            System.out.println(Thread.currentThread().getName() + " released " + conn.getName()
                    + ". Available: " + pool.size());
        }
    }

    public int available() {
        return pool.size();
    }

    // --- inner class representing a connection ---

    static class PooledConnection {
        private final String name;

        PooledConnection(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        void execute(String query) {
            System.out.println(Thread.currentThread().getName()
                    + " [" + name + "] executing: " + query);
        }
    }

    // --- demo ---

    public static void main(String[] args) throws InterruptedException {
        ConnectionPool pool = new ConnectionPool(3);

        // 6 workers competing for 3 connections
        Runnable task = () -> {
            try {
                PooledConnection conn = pool.borrow();
                conn.execute("SELECT * FROM orders");
                Thread.sleep(1000 + (long) (Math.random() * 2000));
                pool.release(conn);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread[] threads = new Thread[6];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(task, "Worker-" + i);
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("Done. Available connections: " + pool.available());
    }
}
