package compozitor.engine.core.interfaces;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.tools.FileObject;
import com.google.common.io.CharStreams;
import compozitor.generator.core.interfaces.GeneratedCode;
import compozitor.generator.core.interfaces.MetamodelRepository;
import compozitor.generator.core.interfaces.TemplateMetadata;
import compozitor.processor.core.interfaces.AnnotationProcessor;
import compozitor.template.core.interfaces.Template;
import compozitor.template.core.interfaces.TemplateContextData;
import compozitor.template.core.interfaces.TemplateEngine;
import compozitor.template.core.interfaces.TemplateEngineBuilder;

public abstract class ProcessorEngine<T extends TemplateContextData<T>> extends AnnotationProcessor {
  private final CodeEngine<T> engine;
  private final EngineContext<T> context;
  private final TemplateEngine templateEngine;
  private final EngineType engineType;
  private final MetamodelRepository<T> repository;
  private StateHandler stateHandler;

  public ProcessorEngine() {
    this.engine = CodeEngine.create();
    this.context = EngineContext.create();
    this.templateEngine = this.init(TemplateEngineBuilder.create().withClasspathTemplateLoader());
    this.repository = new MetamodelRepository<>();
    this.stateHandler = ((ise) -> {
      throw new RuntimeException(ise);
    });
    this.engineType = EngineType.adapter(this.getTargetAnnotation().getSimpleName());
    this.context.add(engineType, this.repository);
  }

  protected TemplateEngine init(TemplateEngineBuilder builder) {
    return builder.build();
  }
  
  protected void add(T metadata) {
    this.repository.add(metadata);
  }

  @Override
  protected final void postProcess() {
    List<TemplateMetadata> templates = new ArrayList<>();
    this.loadTemplates(templates);
    templates.forEach(template ->{
      this.context.add(this.engineType, template);
    });

    this.engine.generate(context, code -> {
      this.write(code);
    });
  }

  private void write(GeneratedCode code) {
    Filer filer = this.processingEnv.getFiler();

    try {
      String sourceCode = CharStreams.toString(new InputStreamReader(code.getContent()));

      FileObject sourceFile = filer.createSourceFile(code.getQualifiedName());

      try (Writer writer = sourceFile.openWriter()) {
        writer.write(sourceCode);
      }
    } catch (FilerException fe) {
      this.stateHandler.accept(fe);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void setHandler(StateHandler handler) {
    this.stateHandler = handler;
  }
  
  @Override
  public final Set<String> getSupportedAnnotationTypes() {
    return new HashSet<String>(Arrays.asList(this.getTargetAnnotation().getCanonicalName()));
  }

  protected final Template getTemplate(String resourceName) {
    return this.templateEngine.getTemplate(resourceName);
  }
  
  protected abstract Class<? extends Annotation> getTargetAnnotation();

  protected abstract void loadTemplates(List<TemplateMetadata> templates);
}
