package bench;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.google.common.io.CharStreams;

public abstract class V2MappingBenchmark {
	protected static final String MAPPINGS = readMappings();
	protected static final byte[] RAW_MAPPINGS = MAPPINGS.getBytes(StandardCharsets.UTF_8); 

	private static String readMappings() {
		try (Reader in = new InputStreamReader(V2MappingBenchmark.class.getResourceAsStream("/mappingsV2.tiny"), StandardCharsets.UTF_8)) {
			return CharStreams.toString(in);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read mappings?", e);
		}
	}
}