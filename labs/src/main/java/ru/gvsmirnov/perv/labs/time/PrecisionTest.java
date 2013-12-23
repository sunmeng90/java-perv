package ru.gvsmirnov.perv.labs.time;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static ru.gvsmirnov.perv.labs.util.Util.*;
 
public class PrecisionTest {

    private static final long DEFAULT_MAX_RESOLUTION = TimeUnit.SECONDS.toNanos(2);
    private static final long DEFAULT_MIN_RESOLUTION = TimeUnit.NANOSECONDS.toNanos(100);

    @Option(name = "--max", usage = "Max resolution to be tested")
    private long maxResolution = DEFAULT_MAX_RESOLUTION;

    @Option(name = "--min", usage = "Min resolution to be tested")
    private long minResolution = DEFAULT_MIN_RESOLUTION;

    @Option(name = "-v", usage = "Verbose")
    private boolean verbose = false;

    @Option(name = "-q", usage = "Quiet")
    private boolean quiet = false;

    private long estimatePrecision(TimeKiller killer, String name) {
        if(!quiet) out("Estimating the precision of %s...", name);

        long left = minResolution, right = maxResolution;
        long lastSuccessfulResolution = -1;


        while(right - left >= minResolution) {
            if(verbose) {
                out("Current interval: (%d, %d)", left, right);
            }

            long currentResolution = (left + right) / 2;
            boolean precise = isPrecise(killer, currentResolution, (int) (maxResolution / currentResolution));

            if(precise) {
                right = currentResolution - 1;
                lastSuccessfulResolution= currentResolution;
            } else {
                left = currentResolution + 1;
            }

        }

        if(!quiet) out("Precision of %s is %s", name, annotate(lastSuccessfulResolution));

        return lastSuccessfulResolution;
    }
 
    private boolean isPrecise(TimeKiller timeKiller, long resolution, int iterations) {
 
        if(verbose) {
            out("Testing resolution: %s in %d iterations", annotate(resolution), iterations);
        }

        final long timeToKill = resolution * iterations;

        long elapsedNanoTime = 0;
 
        for(long killed = 0; killed < timeToKill; killed += resolution) {

            long startNanos  = System.nanoTime();
            timeKiller.kill(resolution);
            long endNanos  = System.nanoTime();
 
            elapsedNanoTime += (endNanos - startNanos);

            long shouldHaveElapsed = killed + resolution;

            long totalOverhead = elapsedNanoTime - shouldHaveElapsed;

            if(totalOverhead < 0) {
                throw new AssertionError(
                        String.format(
                                "Should have elapsed at least %d, elapsed only %d (%.3f iterations lost)",
                                shouldHaveElapsed, elapsedNanoTime, (-1.0 * totalOverhead) / resolution
                        )
                );
            }

            long overheadPerInvocation = (totalOverhead) / (shouldHaveElapsed / resolution);

            if(overheadPerInvocation > resolution) {
                if(verbose) {
                    out("Cumulative error has exceeded allowed threshold, aborting." +
                        "\n\tElapsed time: %s" +
                        "\n\tExpected time: %s" +
                        "\n\tOverhead per invocation: %s\n",
                        annotate(elapsedNanoTime), annotate(shouldHaveElapsed), annotate(overheadPerInvocation)
                    );
                }
                return false;
            }
        }
 
        return true;
    }

    private static final int WARMUP_ITERATIONS = 10_000 * 2; //just to be sure

    private static void warmup() {
        out("%s Warming up...", new Date());


        PrecisionTest precisionTest = new PrecisionTest();
            precisionTest.quiet = true;
            precisionTest.verbose = false;
            precisionTest.minResolution = 1;
            precisionTest.maxResolution = 3;


        Collection<TimeKiller> timeKillers = Arrays.asList(
                new TimeKiller.Sleeper(),
                new TimeKiller.Parker(),
                new TimeKiller.Burner(),
                new TimeKiller.BlackHole(1)
        );


        final int reportEvery = WARMUP_ITERATIONS / 100;
        final int newLineEvery = reportEvery * 10;


        for(int iteration = 1; iteration <= WARMUP_ITERATIONS; iteration++) {
            for(TimeKiller timeKiller : timeKillers) {
                precisionTest.estimatePrecision(timeKiller, null);
            }

            if(iteration % reportEvery == 0)
                outClear(".");

            if(iteration % newLineEvery == 0)
                out();
        }

        out("%s Warmup done!", new Date());

    }

    public static void main(String[] args) throws CmdLineException {

        warmup();

        PrecisionTest precisionTest = new PrecisionTest();
        new CmdLineParser(precisionTest).parseArgument(args);

        precisionTest.estimatePrecision(new TimeKiller.Sleeper(), "Thread.sleep()");
        precisionTest.estimatePrecision(new TimeKiller.Parker(), "LockSupport.parkNanos()");
        precisionTest.estimatePrecision(new TimeKiller.Burner(), "System.nanoTime()");


        out("Estimating the number of BlackHole tokens per nano...");
        double tokensPerNano = TimeKiller.BlackHole.estimateTokensPerNano();
        out("BlackHole tokens per nano: %.4f", tokensPerNano);

        precisionTest.estimatePrecision(new TimeKiller.BlackHole(tokensPerNano), "BlackHole.consumeCPU()");

    }
}
