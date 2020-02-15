package bench;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;

import com.chocohead.mappings.MappingsProvider;

@Fork(25)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class V1Full extends V1MappingBenchmark {
	@Benchmark
	public void measureFabricBig(Blackhole hole) throws IOException {
		hole.consume(net.fabricmc.mappings.MappingsProvider.readTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), false));
	}

	@Benchmark
	public void measureFabricSmall(Blackhole hole) throws IOException {
		hole.consume(net.fabricmc.mappings.MappingsProvider.readTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), true));
	}

	@Benchmark
	public void measureChocoBig(Blackhole hole) throws IOException {
		hole.consume(MappingsProvider.readTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), false));
	}

	@Benchmark
	public void measureChocoSmall(Blackhole hole) throws IOException {
		hole.consume(MappingsProvider.readTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), true));
	}

	@Benchmark
	public void measureChocoFakeFull(Blackhole hole) throws IOException {
		hole.consume(MappingsProvider.readFullTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), false));
	}
}