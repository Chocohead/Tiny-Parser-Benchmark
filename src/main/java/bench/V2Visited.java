package bench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import net.fabricmc.mapping.reader.v2.TinyMetadata;
import net.fabricmc.mapping.reader.v2.TinyV2Factory;
import net.fabricmc.mapping.reader.v2.TinyVisitor;

import com.chocohead.mappings.TinyV2Visitor;
import com.chocohead.mappings.visitor.ClassVisitor;
import com.chocohead.mappings.visitor.FieldVisitor;
import com.chocohead.mappings.visitor.LocalVisitor;
import com.chocohead.mappings.visitor.MappingsVisitor;
import com.chocohead.mappings.visitor.MethodVisitor;
import com.chocohead.mappings.visitor.ParameterVisitor;

@Fork(25)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class V2Visited extends V2MappingBenchmark {
	@Benchmark
	public void measureFabric() throws IOException {
		TinyV2Factory.visit(new BufferedReader(new StringReader(MAPPINGS)), new TinyVisitor() {
			@Override
			public void start(TinyMetadata metadata) {
				assert metadata.getMajorVersion() == 2;
			}
		});
	}

	@Benchmark
	public void measureChocoClass() throws IOException {
		TinyV2Visitor.read(new StringReader(MAPPINGS), new MappingsVisitor() {

			@Override
			public void visitVersion(int major, int minor) {
				assert major == 2;
			}

			@Override
			public void visitNamespaces(String... namespaces) {
			}

			@Override
			public void visitProperty(String name) {
			}

			@Override
			public void visitProperty(String name, String value) {
			}

			@Override
			public ClassVisitor visitClass(long offset, String[] names) {
				return null; //Not doing anything with it
			}
		});
	}

	@Benchmark
	public void measureChocoMember() throws IOException {
		TinyV2Visitor.read(new StringReader(MAPPINGS), new MappingsVisitor() {

			@Override
			public void visitVersion(int major, int minor) {
				assert major == 2;
			}

			@Override
			public void visitNamespaces(String... namespaces) {
			}

			@Override
			public void visitProperty(String name) {
			}

			@Override
			public void visitProperty(String name, String value) {
			}

			@Override
			public ClassVisitor visitClass(long offset, String[] names) {
				return new ClassVisitor() {

					@Override
					public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
						return null;
					}

					@Override
					public FieldVisitor visitField(long offset, String[] names, String descriptor) {
						return null;
					}

					@Override
					public void visitComment(String line) {
					}
					
				};
			}
		});
	}

	@Benchmark
	public void measureChocoMemberCached() throws IOException {
		TinyV2Visitor.read(new StringReader(MAPPINGS), new MappingsVisitor() {
			private final ClassVisitor cv = new ClassVisitor() {

				@Override
				public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
					return null;
				}

				@Override
				public FieldVisitor visitField(long offset, String[] names, String descriptor) {
					return null;
				}

				@Override
				public void visitComment(String line) {
				}
			};

			@Override
			public void visitVersion(int major, int minor) {
				assert major == 2;
			}

			@Override
			public void visitNamespaces(String... namespaces) {
			}

			@Override
			public void visitProperty(String name) {
			}

			@Override
			public void visitProperty(String name, String value) {
			}

			@Override
			public ClassVisitor visitClass(long offset, String[] names) {
				return cv;
			}
		});
	}
	
	@Benchmark
	public void measureChocoMemberPlus() throws IOException {
		TinyV2Visitor.read(new StringReader(MAPPINGS), new MappingsVisitor() {

			@Override
			public void visitVersion(int major, int minor) {
				assert major == 2;
			}

			@Override
			public void visitNamespaces(String... namespaces) {
			}

			@Override
			public void visitProperty(String name) {
			}

			@Override
			public void visitProperty(String name, String value) {
			}

			@Override
			public ClassVisitor visitClass(long offset, String[] names) {
				return new ClassVisitor() {

					@Override
					public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
						return new MethodVisitor() {
							
							@Override
							public ParameterVisitor visitParameter(long offset, String[] names, int localVariableIndex) {
								return null;
							}
							
							@Override
							public LocalVisitor visitLocalVariable(long offset, String[] names, int localVariableIndex, int localVariableStartOffset, int localVariableTableIndex) {
								return null;
							}
							
							@Override
							public void visitComment(String line) {
							}
						};
					}

					@Override
					public FieldVisitor visitField(long offset, String[] names, String descriptor) {
						return new FieldVisitor() {
							
							@Override
							public void visitComment(String line) {
							}
						};
					}

					@Override
					public void visitComment(String line) {
					}
				};
			}
		});
	}
	
	@Benchmark
	public void measureChocoAll() throws IOException {
		TinyV2Visitor.read(new StringReader(MAPPINGS), new MappingsVisitor() {

			@Override
			public void visitVersion(int major, int minor) {
				assert major == 2;
			}

			@Override
			public void visitNamespaces(String... namespaces) {
			}

			@Override
			public void visitProperty(String name) {
			}

			@Override
			public void visitProperty(String name, String value) {
			}

			@Override
			public ClassVisitor visitClass(long offset, String[] names) {
				return new ClassVisitor() {

					@Override
					public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
						return new MethodVisitor() {
							
							@Override
							public ParameterVisitor visitParameter(long offset, String[] names, int localVariableIndex) {
								return new ParameterVisitor() {
									
									@Override
									public void visitComment(String line) {
									}
								};
							}
							
							@Override
							public LocalVisitor visitLocalVariable(long offset, String[] names, int localVariableIndex, int localVariableStartOffset, int localVariableTableIndex) {
								return new LocalVisitor() {
									
									@Override
									public void visitComment(String line) {
									}
								};
							}
							
							@Override
							public void visitComment(String line) {
							}
						};
					}

					@Override
					public FieldVisitor visitField(long offset, String[] names, String descriptor) {
						return new FieldVisitor() {
							
							@Override
							public void visitComment(String line) {
							}
						};
					}

					@Override
					public void visitComment(String line) {
					}
				};
			}
		});
	}
}