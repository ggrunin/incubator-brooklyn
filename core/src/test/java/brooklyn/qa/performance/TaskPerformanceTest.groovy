package brooklyn.qa.performance

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.util.task.BasicExecutionManager
import brooklyn.util.task.SingleThreadedScheduler

public class TaskPerformanceTest extends AbstractPerformanceTest {

    protected static final Logger LOG = LoggerFactory.getLogger(TaskPerformanceTest.class)
    
    private static final long LONG_TIMEOUT_MS = 30*1000
    
    BasicExecutionManager executionManager
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        super.setUp()
        
        app.start([loc])
        
        executionManager = app.managementContext.executionManager
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testExecuteSimplestRunnable() {
        int numIterations = 10000
        double minRatePerSec = 1000
        
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1)
        
        Runnable work = new Runnable() { public void run() {
                int val = counter.incrementAndGet()
                if (val >= numIterations) completionLatch.countDown()
            }}

        measureAndAssert("executeSimplestRunnable", numIterations, minRatePerSec,
                { executionManager.submit(work) },
                { completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS); assertTrue(completionLatch.getCount() <= 0) })
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testExecuteRunnableWithTags() {
        int numIterations = 10000
        double minRatePerSec = 1000
        
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1)

        Runnable work = new Runnable() { public void run() {
                int val = counter.incrementAndGet()
                if (val >= numIterations) completionLatch.countDown()
            }}

        Map flags = [tags:["a","b"]]
        
        measureAndAssert("testExecuteRunnableWithTags", numIterations, minRatePerSec,
                { executionManager.submit(flags, work) },
                { completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS); assertTrue(completionLatch.getCount() <= 0) })
    }
    
    @Test(groups=["Integration", "Acceptance"])
    public void testExecuteWithSingleThreadedScheduler() {
        int numIterations = 10000
        double minRatePerSec = 1000

        executionManager.setTaskSchedulerForTag("singlethreaded", SingleThreadedScheduler.class);
        
        final AtomicInteger concurrentCallCount = new AtomicInteger();
        final AtomicInteger counter = new AtomicInteger();
        final CountDownLatch completionLatch = new CountDownLatch(1)
        final List<Exception> exceptions = new CopyOnWriteArrayList()
        
        Runnable work = new Runnable() { public void run() {
                int numConcurrentCalls = concurrentCallCount.incrementAndGet()
                try {
                    if (numConcurrentCalls > 1) throw new IllegalStateException("numConcurrentCalls=$numConcurrentCalls")
                    int val = counter.incrementAndGet()
                    if (val >= numIterations) completionLatch.countDown()
                } catch (Exception e) {
                    exceptions.add(e)
                    LOG.warn("Exception in runnable of testExecuteWithSingleThreadedScheduler", e)
                    throw e
                } finally {
                    concurrentCallCount.decrementAndGet()
                }
            }}

        measureAndAssert("testExecuteWithSingleThreadedScheduler", numIterations, minRatePerSec,
                { executionManager.submit([tags:["singlethreaded"]], work) },
                { completionLatch.await(LONG_TIMEOUT_MS, TimeUnit.MILLISECONDS); assertTrue(completionLatch.getCount() <= 0) })
        
        if (exceptions.size() > 0) throw exceptions.get(0)
    }
}