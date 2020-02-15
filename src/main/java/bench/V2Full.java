package bench;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.infra.Blackhole;

import net.fabricmc.mappings.model.V2MappingsProvider;

import com.chocohead.mappings.MappingsProvider;

@Fork(25)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class V2Full extends V2MappingBenchmark {
	@Benchmark
	public void measureFabric(Blackhole hole) throws IOException {
		hole.consume(V2MappingsProvider.readTinyMappings(new BufferedReader(new StringReader(MAPPINGS))));
	}
	
	@Benchmark
	public void measureChocoBig(Blackhole hole) throws IOException {
		hole.consume(MappingsProvider.readFullTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), false));
	}
	
	@Benchmark
	public void measureChocoSmall(Blackhole hole) throws IOException {
		hole.consume(MappingsProvider.readFullTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), true));
	}
	
	@Benchmark
	public void measureChocoShortBig(Blackhole hole) throws IOException {
		hole.consume(MappingsProvider.readTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), false));
	}
	
	@Benchmark
	public void measureChocoShortSmall(Blackhole hole) throws IOException {
		hole.consume(MappingsProvider.readTinyMappings(new ByteArrayInputStream(RAW_MAPPINGS), true));
	}
}