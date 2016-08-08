package io.github.swagger2markup.internal.document.builder;

import io.github.swagger2markup.Swagger2MarkupConverter;
import io.github.swagger2markup.Swagger2MarkupExtensionRegistry;
import io.github.swagger2markup.internal.document.MarkupDocument;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ResourceBundle;

/**
 * Created by eleonore on 05/08/16.
 */
public class ModelDocumentBuilder extends MarkupDocumentBuilder {

    public ModelDocumentBuilder(Swagger2MarkupConverter.Context context, Swagger2MarkupExtensionRegistry extensionRegistry, Path outputPath){
        super(context, extensionRegistry, outputPath);

        ResourceBundle labels = ResourceBundle.getBundle("io/github/swagger2markup/lang/labels", config.getOutputLanguage().toLocale());
    }

    @Override
    public MarkupDocument build() throws IOException {
        return null;
    }


}
