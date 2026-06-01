package cn.yy.myrent.lab;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================
 *  synchronized 教学实验
 * ============================================================
 *
 *  先理解一个比喻：
 *
 *    想象一个公共厕所，只有一个坑位，门上有一把锁。
 *    - 进去的人把门锁上（获得锁）
 *    - 外面的人只能等（阻塞）
 *    - 出来后开锁（释放锁），下一个人才能进
 *
 *    synchronized 就是这把锁。
 *    "锁"在 Java 里就是一个普通对象，每个对象天生自带一把锁。
 *
 *  为什么需要锁？
 *
 *    count++ 看起来是一步，实际上 CPU 执行了三步：
 *      1. 读取 count 的值
 *      2. 把值加 1
 *      3. 把新值写回 count
 *
 *    两个线程同时执行，可能都在第1步读到同一个值，
 *    然后各自加1写回，结果只加了1次，丢了一次。
 *    这就是"竞态条件"。
 *
 *  synchronized 能写在 4 个位置：
 *    1. 实例方法上     → 锁是 this（当前对象）
 *    2. 静态方法上     → 锁是 Class 对象
 *    3. 代码块(this)   → 锁是 this，但只锁一部分代码
 *    4. 代码块(自定义) → 锁是你指定的任意对象
 * ============================================================
 */
class SynchronizedLabTest {

    // =========================================================
    // 实验 0：先看没有锁时会发生什么
    //         理解"为什么需要锁"
    // =========================================================

    static class NoLockCounter {
        int count = 0;

        // 把 count++ 拆成三步，每步之间 sleep，模拟 CPU 在任意时刻切换线程
        void increment(String threadName) throws InterruptedException {
            // 第1步：读取当前值
            int current = count;
            System.out.println(threadName + " 读到 count = " + current);

            // 模拟 CPU 切换到另一个线程（现实中这个间隔是纳秒级，这里放大到可见）
            Thread.sleep(10);

            // 第2步：计算新值
            int newValue = current + 1;

            // 第3步：写回
            count = newValue;
            System.out.println(threadName + " 写回 count = " + newValue + "  ← 实际 count 现在是 " + count);
        }
    }

    @Test
    void 实验0_慢动作演示_两个线程互相覆盖() throws InterruptedException {
        NoLockCounter counter = new NoLockCounter();

        // 只跑 3 次，慢动作看清楚每一步
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                try { counter.increment("【线程1】"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                try { counter.increment("【线程2】"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });

        System.out.println("===== 开始，两个线程各加3次，期望结果 = 6 =====");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("===== 结束，实际 count = " + counter.count + " =====");
        System.out.println("→ 看上面的日志：两个线程都读到了同一个值，写回时互相覆盖，所以结果小于6");
        System.out.println("→ 这就是「竞态条件」：count++ 不是原子操作，三步之间可以被打断");
    }

    // =========================================================
    // 实验 1：synchronized 加在实例方法上
    //         锁 = this（调用这个方法的那个对象）
    // =========================================================

    static class InstanceMethodCounter {
        int count = 0;

        // 加了 synchronized：进入这个方法前必须先拿到 this 这把锁
        // 同一时刻只有一个线程能执行这个方法，另一个线程在门口等
        synchronized void increment(String threadName) throws InterruptedException {
            System.out.println(threadName + " 拿到锁，开始操作，count = " + count);

            // 第1步：读取
            int current = count;
            Thread.sleep(10); // 放慢，让另一个线程有机会来敲门

            // 第2步+第3步：加1写回
            count = current + 1;
            System.out.println(threadName + " 完成，写回 count = " + count + "，释放锁");
            // 方法结束时自动释放锁，等待的线程才能进来
        }
    }

    @Test
    void 实验1_synchronized加在实例方法上_线程必须排队() throws InterruptedException {
        InstanceMethodCounter counter = new InstanceMethodCounter();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                try { counter.increment("【线程1】"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 3; i++) {
                try { counter.increment("【线程2】"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });

        System.out.println("===== 开始，两个线程各加3次，期望结果 = 6 =====");
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(counter.count).isEqualTo(6);
        System.out.println("===== 结束，实际 count = " + counter.count + " =====");
        System.out.println("→ 对比实验0：这次每次「拿到锁」和「释放锁」严格交替，不会同时读到同一个值");
        System.out.println("→ 锁是 this，也就是 counter 这个对象本身");
    }

    // =========================================================
    // 实验 2：synchronized 加在静态方法上
    //         锁 = Class 对象（不是某个实例，是这个类本身）
    //
    //         关键区别：
    //         实例方法锁 → 不同实例有不同的锁，互不影响
    //         静态方法锁 → 所有实例共用同一把锁（Class 对象只有一个）
    // =========================================================

    static class StaticMethodCounter {
        static int count = 0;

        // 锁的是 StaticMethodCounter.class，全局只有一个
        static synchronized void increment() {
            count++;
        }

        static void reset() { count = 0; }
    }

    @Test
    void 实验2_synchronized加在静态方法上_所有实例共用一把锁() throws InterruptedException {
        StaticMethodCounter.reset();

        // 注意：这里创建了两个不同的实例
        // 但因为 increment 是 static synchronized，锁的是 Class，不是实例
        // 所以两个线程仍然互斥
        StaticMethodCounter obj1 = new StaticMethodCounter();
        StaticMethodCounter obj2 = new StaticMethodCounter();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                obj1.increment(); // 实际上等价于 StaticMethodCounter.increment()
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                obj2.increment(); // 和 t1 用的是同一把锁（Class 对象）
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(StaticMethodCounter.count).isEqualTo(20000);
        System.out.println("静态方法锁结果: " + StaticMethodCounter.count + "  期望: 20000 ✓");
        System.out.println("→ obj1 和 obj2 是不同实例，但 static synchronized 锁的是 Class，所以仍然互斥");
    }

    // =========================================================
    // 实验 3：synchronized 代码块，锁 this
    //         和实验1效果相同，但只锁"最关键的那几行"
    //
    //         为什么要用代码块而不是直接锁整个方法？
    //         → 锁的范围越小，其他线程等待的时间越短，性能越好
    // =========================================================

    static class BlockCounter {
        int count = 0;

        void increment() {
            // 假设这里有一些不需要保护的准备工作（比如打日志、参数校验）
            // 这部分不加锁，多个线程可以同时执行，不影响正确性

            synchronized (this) {
                // 只有这一行真正需要保护
                count++;
            }

            // 假设这里还有一些不需要保护的收尾工作
        }
    }

    @Test
    void 实验3_synchronized代码块锁this_只保护关键代码() throws InterruptedException {
        BlockCounter counter = new BlockCounter();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                counter.increment();
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                counter.increment();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(counter.count).isEqualTo(20000);

        System.out.println("代码块锁结果: " + counter.count + "  期望: 20000 ✓");
        System.out.println("→ synchronized(this) 和加在方法上效果一样，但锁的范围更小");
    }

    // =========================================================
    // 实验 4：synchronized 代码块，锁自定义对象
    //         精髓：用不同的锁对象保护不同的数据，互不干扰
    //
    //         比喻：两个厕所，各有各的锁
    //         用厕所A的人不影响用厕所B的人
    // =========================================================

    static class TwoLockCounter {
        int countA = 0;
        int countB = 0;

        // 两把独立的锁，分别保护各自的数据
        private final Object lockA = new Object();
        private final Object lockB = new Object();

        void incrementA() {
            synchronized (lockA) { // 只锁 lockA，不影响 lockB
                countA++;
            }
        }

        void incrementB() {
            synchronized (lockB) { // 只锁 lockB，不影响 lockA
                countB++;
            }
        }
    }

    @Test
    void 实验4_不同锁对象保护不同数据_两组操作互不干扰() throws InterruptedException {
        TwoLockCounter counter = new TwoLockCounter();

        // t1 只操作 countA，t2 只操作 countB
        // 因为用的是不同的锁，t1 和 t2 可以真正同时运行，互不等待
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                counter.incrementA();
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                counter.incrementB();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(counter.countA).isEqualTo(10000);
        assertThat(counter.countB).isEqualTo(10000);
        System.out.println("lockA 保护的 countA: " + counter.countA + "  期望: 10000 ✓");
        System.out.println("lockB 保护的 countB: " + counter.countB + "  期望: 10000 ✓");
        System.out.println("→ 两把锁互不干扰，t1 和 t2 可以真正并发，性能更好");
    }

    // =========================================================
    // 实验 5：面试陷阱 —— 锁对象不固定，等于没加锁
    //
    //         synchronized 能保护的前提：
    //         所有线程必须争同一个对象的锁
    //         如果每次 new 一个新对象，每个线程拿到的锁都不同，
    //         根本不存在竞争，也就没有互斥效果
    // =========================================================

    static class WrongLockCounter {
        int count = 0;

        void increment() {
            // 每次调用都 new 一个新对象作为锁
            // 线程1拿到锁A，线程2拿到锁B，两把不同的锁，互不影响
            // 结果：完全没有保护，和没加锁一样
            synchronized (new Object()) {
                count++;
            }
        }
    }

    @Test
    void 实验5_陷阱_每次new新对象作为锁_等于没有锁() throws InterruptedException {
        WrongLockCounter counter = new WrongLockCounter();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                counter.increment();
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10000; i++) {
                counter.increment();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // 大概率小于 20000，因为根本没有互斥
        System.out.println("错误锁结果: " + counter.count + "  期望: 20000（大概率偏小）");
        System.out.println("→ synchronized(new Object()) 每次锁不同对象，完全没有保护！");
        System.out.println("→ 面试常考：看起来加了锁，实际上没有任何互斥效果");
    }
}
