package cn.yy.myrent.lab;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ============================================================
 *  synchronized vs Lock 教学实验
 * ============================================================
 *
 *  synchronized 和 Lock 都能实现互斥，但有 4 个关键区别：
 *
 *  ┌────┬──────────────────────┬──────────────────────────────┐
 *  │    │  synchronized        │  Lock (ReentrantLock)        │
 *  ├────┼──────────────────────┼──────────────────────────────┤
 *  │ 1  │ 自动释放锁           │ 必须手动 unlock()            │
 *  │ 2  │ 拿不到锁只能死等     │ tryLock() 可以放弃等待       │
 *  │ 3  │ 等待中无法响应中断   │ lockInterruptibly() 可以     │
 *  │ 4  │ 非公平（默认）       │ 可选公平/非公平              │
 *  └────┴──────────────────────┴──────────────────────────────┘
 *
 *  每个实验都会先演示 synchronized 的局限，再演示 Lock 如何解决。
 * ============================================================
 */
class SynchronizedVsLockLabTest {

    // =========================================================
    // 实验 1：自动释放 vs 手动释放
    //
    //  synchronized：方法/代码块结束时自动释放，即使抛了异常也会释放
    //  Lock：必须自己调 unlock()，忘了写或者中间抛异常没释放 → 死锁
    //
    //  这个实验演示：Lock 忘记 unlock 会怎样
    // =========================================================

    static final ReentrantLock lock1 = new ReentrantLock();
    static int sharedValue = 0;

    @Test
    void 实验1A_synchronized_异常时自动释放锁() throws InterruptedException {
        Object lock = new Object();

        Thread t1 = new Thread(() -> {
            synchronized (lock) {
                System.out.println("【线程1】拿到锁，准备抛异常...");
                try {
                    Thread.sleep(100);
                    throw new RuntimeException("模拟业务异常");
                } catch (Exception e) {
                    System.out.println("【线程1】发生异常: " + e.getMessage());
                    // synchronized 块结束，锁自动释放，不需要任何额外代码
                }
                // ← 到这里锁已经自动释放了
            }
            System.out.println("【线程1】锁已自动释放");
        });

        Thread t2 = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("【线程2】等待获取锁...");
            synchronized (lock) {
                System.out.println("【线程2】成功拿到锁！synchronized 异常后自动释放了 ✓");
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    @Test
    void 实验1B_Lock_忘记unlock_导致其他线程永远等待() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        Thread t1 = new Thread(() -> {
            lock.lock();
            System.out.println("【线程1】拿到锁，但是发生了异常，忘记 unlock...");
            try {
                Thread.sleep(100);
                throw new RuntimeException("模拟业务异常");
            } catch (Exception e) {
                System.out.println("【线程1】发生异常: " + e.getMessage());
                // 忘记写 lock.unlock()！锁永远不会释放！
            }finally {
                lock.unlock();
            }
            // 注意：这里没有 lock.unlock()
        });

        Thread t2 = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("【线程2】尝试获取锁，最多等 500ms...");
            try {
                boolean acquired = lock.tryLock(500, TimeUnit.MILLISECONDS);
                if (acquired) {
                    System.out.println("【线程2】拿到锁了");
                    lock.unlock();
                } else {
                    System.out.println("【线程2】等了 500ms 还没拿到锁，放弃！← 这就是忘记 unlock 的后果");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("→ 正确写法是把 unlock() 放在 finally 块里，保证一定执行");
        System.out.println("→ lock.lock() 之后必须：try { ... } finally { lock.unlock(); }");
    }

    @Test
    void 实验1C_Lock_正确写法_finally保证释放() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        Thread t1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("【线程1】拿到锁，准备抛异常...");
                Thread.sleep(100);
                throw new RuntimeException("模拟业务异常");
            } catch (Exception e) {
                System.out.println("【线程1】发生异常: " + e.getMessage());
            } finally {
                lock.unlock(); // finally 保证无论如何都会执行
                System.out.println("【线程1】finally 中释放了锁 ✓");
            }
        });

        Thread t2 = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("【线程2】等待获取锁...");
            lock.lock();
            try {
                System.out.println("【线程2】成功拿到锁！finally 保证了锁一定被释放 ✓");
            } finally {
                lock.unlock();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    // =========================================================
    // 实验 2：死等 vs tryLock 放弃等待
    //
    //  synchronized：拿不到锁就一直阻塞，没有任何办法
    //  Lock.tryLock()：等一段时间拿不到就放弃，可以做其他事
    //
    //  场景：抢票系统，锁被占用时不死等，而是告诉用户"稍后再试"
    // =========================================================

    @Test
    void 实验2A_synchronized_拿不到锁只能死等() throws InterruptedException {
        Object lock = new Object();

        // 线程1占住锁 2 秒
        Thread t1 = new Thread(() -> {
            synchronized (lock) {
                System.out.println("【线程1】占住锁，模拟耗时操作，2秒后释放...");
                try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                System.out.println("【线程1】释放锁");
            }
        });

        // 线程2只能死等，没有任何超时机制
        Thread t2 = new Thread(() -> {
            System.out.println("【线程2】等待锁...（synchronized 只能死等，没有超时）");
            long start = System.currentTimeMillis();
            synchronized (lock) {
                long waited = System.currentTimeMillis() - start;
                System.out.println("【线程2】终于拿到锁，等了 " + waited + "ms（只能等，没有选择）");
            }
        });

        t1.start();
        Thread.sleep(100);
        t2.start();
        t1.join();
        t2.join();
    }

    @Test
    void 实验2B_Lock_tryLock_拿不到可以放弃() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        // 线程1占住锁 2 秒
        Thread t1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("【线程1】占住锁，模拟耗时操作，2秒后释放...");
                Thread.sleep(10000);
                System.out.println("【线程1】释放锁");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });

        // 线程2最多等 500ms，等不到就放弃，去做别的事
        Thread t2 = new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            System.out.println("【线程2】尝试获取锁，最多等 500ms...");
            try {
                boolean acquired = lock.tryLock(2000, TimeUnit.MILLISECONDS);
                if (acquired) {
                    try {
                        System.out.println("【线程2】拿到锁了，处理业务");
                    } finally {
                        lock.unlock();
                    }
                } else {
                    // 拿不到锁，可以做其他事，而不是傻等
                    System.out.println("【线程2】500ms 内没拿到锁，放弃，返回「系统繁忙，请稍后重试」✓");
                    System.out.println("→ 这就是 tryLock 的价值：给用户更好的体验，而不是让请求卡死");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    // =========================================================
    // 实验 3：不可中断 vs 可中断
    //
    //  synchronized：线程在等锁时，即使你调 interrupt()，它也不理会，继续等
    //  Lock.lockInterruptibly()：等锁时收到中断信号，立刻抛出 InterruptedException
    //
    //  场景：用户取消了请求，希望等待中的线程立刻停下来
    // =========================================================

    @Test
    void 实验3A_synchronized_等待时无法响应中断() throws InterruptedException {
        Object lock = new Object();

        Thread t1 = new Thread(() -> {
            synchronized (lock) {
                System.out.println("【线程1】占住锁，3秒后释放");
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });

        Thread t2 = new Thread(() -> {
            System.out.println("【线程2】开始等锁...");
            synchronized (lock) {
                // 能走到这里说明拿到锁了
                System.out.println("【线程2】拿到锁（说明中断没有打断等待过程）");
            }
        });

        t1.start();
        Thread.sleep(100);
        t2.start();
        Thread.sleep(500);

        System.out.println("【主线程】对线程2发出中断信号...");
        t2.interrupt(); // 发出中断，但线程2在等 synchronized 锁，不会响应

        t1.join();
        t2.join();
        System.out.println("→ 线程2忽略了中断，一直等到拿到锁才结束");
        System.out.println("→ synchronized 等待期间无法被中断，用户取消请求也没用");
    }

    @Test
    void 实验3B_Lock_lockInterruptibly_等待时可以响应中断() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        Thread t1 = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("【线程1】占住锁，3秒后释放");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });

        Thread t2 = new Thread(() -> {
            System.out.println("【线程2】开始等锁（用 lockInterruptibly，可以响应中断）...");
            try {
                lock.lockInterruptibly(); // 等锁，但可以被中断
                try {
                    System.out.println("【线程2】拿到锁了");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                // 收到中断信号，立刻从等待中退出
                System.out.println("【线程2】收到中断信号，立刻放弃等锁，退出！✓");
                System.out.println("→ 这就是 lockInterruptibly 的价值：用户取消请求，线程立刻停下来");
            }
        });

        t1.start();
        Thread.sleep(100);
        t2.start();
        Thread.sleep(500);

        System.out.println("【主线程】对线程2发出中断信号...");
        t2.interrupt(); // 线程2正在等锁，收到中断后立刻退出

        t1.join();
        t2.join();
    }

    // =========================================================
    // 实验 4：公平锁 vs 非公平锁
    //
    //  非公平锁（默认）：新来的线程可以插队，不按等待顺序
    //  公平锁：严格按照等待顺序，先来先得
    //
    //  synchronized 只有非公平模式
    //  ReentrantLock(true) 是公平锁，ReentrantLock(false)/ReentrantLock() 是非公平锁
    //
    //  场景：排队叫号系统，公平锁保证先等的人先拿到
    // =========================================================

    @Test
    void 实验4A_非公平锁_新线程可能插队() throws InterruptedException {
        ReentrantLock unfairLock = new ReentrantLock(false); // 非公平锁

        // 先让一个线程占住锁
        unfairLock.lock();

        // 依次启动 5 个线程排队等锁，记录它们的等待顺序
        Thread[] waiters = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int id = i + 1;
            waiters[i] = new Thread(() -> {
                System.out.println("线程" + id + " 开始排队等锁");
                unfairLock.lock();
                try {
                    System.out.println("线程" + id + " 拿到锁（非公平：不保证按排队顺序）");
                } finally {
                    unfairLock.unlock();
                }
            }, "线程" + i);
            waiters[i].start();
            Thread.sleep(50); // 让线程按顺序启动
        }

        Thread.sleep(100);
        System.out.println("【主线程】释放锁，观察哪个线程先拿到...");
        unfairLock.unlock();

        for (Thread t : waiters) t.join();
        System.out.println("→ 非公平锁：顺序不保证，新来的线程可能插到等待队列前面");
    }

    @Test
    void 实验4B_公平锁_严格按等待顺序() throws InterruptedException {
        ReentrantLock fairLock = new ReentrantLock(true); // 公平锁

        // 先让一个线程占住锁
        fairLock.lock();

        // 依次启动 5 个线程排队等锁
        Thread[] waiters = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int id = i + 1;
            waiters[i] = new Thread(() -> {
                System.out.println("线程" + id + " 开始排队等锁");
                fairLock.lock();
                try {
                    System.out.println("线程" + id + " 拿到锁（公平：按排队顺序）");
                } finally {
                    fairLock.unlock();
                }
            }, "线程" + i);
            waiters[i].start();
            Thread.sleep(50); // 让线程按顺序启动，确保等待队列顺序是 1→2→3→4→5
        }

        Thread.sleep(100);
        System.out.println("【主线程】释放锁，观察哪个线程先拿到...");
        fairLock.unlock();

        for (Thread t : waiters) t.join();
        System.out.println("→ 公平锁：严格按 1→2→3→4→5 的顺序，先等的先拿到");
        System.out.println("→ 代价：性能比非公平锁低，因为每次都要检查等待队列");
    }


}
