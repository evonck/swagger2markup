/*
 * Copyright 2016 Robert Winkler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.swagger2markup.internal.document.builder;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Multimap;
import io.github.swagger2markup.GroupBy;
import io.github.swagger2markup.Swagger2MarkupConverter;
import io.github.swagger2markup.Swagger2MarkupExtensionRegistry;
import io.github.swagger2markup.internal.document.MarkupDocument;
import io.github.swagger2markup.internal.type.ObjectType;
import io.github.swagger2markup.internal.type.Type;
import io.github.swagger2markup.internal.utils.ExamplesUtil;
import io.github.swagger2markup.internal.utils.ParameterUtils;
import io.github.swagger2markup.internal.utils.PropertyUtils;
import io.github.swagger2markup.internal.utils.TagUtils;
import io.github.swagger2markup.markup.builder.*;
import io.github.swagger2markup.model.PathOperation;
import io.github.swagger2markup.spi.PathsDocumentExtension;
import io.swagger.models.*;
import io.swagger.models.auth.SecuritySchemeDefinition;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.RefParameter;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.util.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.text.WordUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.github.swagger2markup.internal.utils.ListUtils.toSet;
import static io.github.swagger2markup.internal.utils.MapUtils.toKeySet;
import static io.github.swagger2markup.internal.utils.TagUtils.convertTagsListToMap;
import static io.github.swagger2markup.internal.utils.TagUtils.getTagDescription;
import static io.github.swagger2markup.spi.PathsDocumentExtension.Context;
import static io.github.swagger2markup.spi.PathsDocumentExtension.Position;
import static io.github.swagger2markup.utils.IOUtils.normalizeName;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author Robert Winkler
 */
public class PathsDocumentBuilder extends MarkupDocumentBuilder {

    private final String RESPONSE;
    private final String REQUEST;
    private final String PATHS;
    private final String RESOURCES;
    private final String PARAMETERS;
    private final String REQUEST_HEADER;
    private final String ARGUMENTS;
    private final String BODY_PARAMETER;
    private final String RESPONSES;
    private final String HEADERS_COLUMN;
    private final String EXAMPLE_REQUEST;
    private final String EXAMPLE_REQUEST_SLATE;
    private final String EXAMPLE_RESPONSE;
    private final String EXAMPLE_RESPONSE_SLATE;
    private final String DEFINITIONS;
    private final String TOKEN;

    private final String SECURITY;
    private final String TYPE_COLUMN;
    private final String HTTP_CODE_COLUMN;

    private final String DEPRECATED_OPERATION;
    private final String UNKNOWN;
    private final String HEADER;
    private final String PATH;
    private final String QUERY;
    private final String BODY;

    public static String basePath;

    private static final String PATHS_ANCHOR = "paths";

    public PathsDocumentBuilder(Swagger2MarkupConverter.Context globalContext, Swagger2MarkupExtensionRegistry extensionRegistry, java.nio.file.Path outputPath) {
        super(globalContext, extensionRegistry, outputPath);

        ResourceBundle labels = ResourceBundle.getBundle("io/github/swagger2markup/lang/labels", config.getOutputLanguage().toLocale());
        RESPONSE = labels.getString("response");
        REQUEST_HEADER = "HTTP Request";
        REQUEST = labels.getString("request");
        PATHS = labels.getString("paths");
        RESOURCES = labels.getString("resources");
        PARAMETERS = labels.getString("parameters");
        ARGUMENTS = labels.getString("arguments");
        BODY_PARAMETER = labels.getString("body_parameter");
        RESPONSES = labels.getString("responses");
        HEADERS_COLUMN = labels.getString("headers_column");
        EXAMPLE_REQUEST = labels.getString("example_request");
        EXAMPLE_RESPONSE = labels.getString("example_response");
        SECURITY = labels.getString("security");
        TYPE_COLUMN = labels.getString("type_column");
        HTTP_CODE_COLUMN = labels.getString("http_code_column");
        DEPRECATED_OPERATION = labels.getString("operation.deprecated");
        UNKNOWN = labels.getString("unknown");
        DEFINITIONS = ">Definitions:\n";
        EXAMPLE_REQUEST_SLATE = ">Example request:\n";
        EXAMPLE_RESPONSE_SLATE=">Example Response:\n";
        HEADER = "header";
        TOKEN = "token";
        PATH = "path";
        BODY = "body";
        QUERY = "query";

        if (config.isGeneratedExamplesEnabled()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Include examples is enabled.");
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Include examples is disabled.");
            }
        }

        if (config.isSeparatedOperationsEnabled()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Create separated operation files is enabled.");
            }
            Validate.notNull(outputPath, "Output directory is required for separated operation files!");
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("Create separated operation files is disabled.");
            }
        }
    }

    /**
     * Builds the paths MarkupDocument.
     *
     * @return the paths MarkupDocument
     */
    @Override
    public MarkupDocument build() {
        basePath = globalContext.getSwagger().getSchemes().get(0).toString().toLowerCase() + "://"+ globalContext.getSwagger().getHost();
        Map<String, Path> paths = globalContext.getSwagger().getPaths();
        if (MapUtils.isNotEmpty(paths)) {
            applyPathsDocumentExtension(new Context(Position.DOCUMENT_BEFORE, this.markupDocBuilder));
//            buildPathsTitle();
            applyPathsDocumentExtension(new Context(Position.DOCUMENT_BEGIN, this.markupDocBuilder));
            buildsPathsSection(paths);
            applyPathsDocumentExtension(new Context(Position.DOCUMENT_END, this.markupDocBuilder));
            applyPathsDocumentExtension(new Context(Position.DOCUMENT_AFTER, this.markupDocBuilder));
        }
        return new MarkupDocument(markupDocBuilder);
    }

    private void buildsPathsSection(Map<String, Path> paths) {
        Set<PathOperation> pathOperations = toPathOperationsSet(paths);
        if (CollectionUtils.isNotEmpty(pathOperations)) {
            if (config.getPathsGroupedBy() == GroupBy.AS_IS) {
                for (PathOperation operation : pathOperations) {
                    buildOperation(operation);
                }
            } else {
                Multimap<String, PathOperation> operationsGroupedByTag = TagUtils.groupOperationsByTag(pathOperations, config.getTagOrdering(), config.getOperationOrdering());
                Map<String, Tag> tagsMap = convertTagsListToMap(globalContext.getSwagger().getTags());
                for (String tagName : operationsGroupedByTag.keySet()) {
//                    this.markupDocBuilder.sectionTitleWithAnchorLevel1(WordUtils.capitalize(tagName), tagName + "_resource");

                    Optional<String> tagDescription = getTagDescription(tagsMap, tagName);
                    if (tagDescription.isPresent()) {
                        this.markupDocBuilder.paragraph(tagDescription.get());
                    }

                    for (PathOperation operation : operationsGroupedByTag.get(tagName)) {
                        buildOperation(operation);
                    }
                }
            }
        }
    }

    /**
     * Builds the path title depending on the operationsGroupedBy configuration setting.
     */
    private void buildPathsTitle() {
        if (config.getPathsGroupedBy() == GroupBy.AS_IS) {
            buildPathsTitle(PATHS);
        } else {
            buildPathsTitle(RESOURCES);
        }
    }

    /**
     * Converts the Swagger paths into a list PathOperations.
     *
     * @param paths the Swagger paths
     * @return the path operations
     */
    private Set<PathOperation> toPathOperationsSet(Map<String, Path> paths) {
        Set<PathOperation> pathOperations;
        if (config.getOperationOrdering() != null) {
            pathOperations = new TreeSet<>(config.getOperationOrdering());
        } else {
            pathOperations = new LinkedHashSet<>();
        }
        for (Map.Entry<String, Path> path : paths.entrySet()) {
            Map<HttpMethod, Operation> operations = path.getValue().getOperationMap(); // TODO AS_IS does not work because of https://github.com/swagger-api/swagger-core/issues/1696
            if (MapUtils.isNotEmpty(operations)) {
                for (Map.Entry<HttpMethod, Operation> operation : operations.entrySet()) {
                    pathOperations.add(new PathOperation(operation.getKey(), path.getKey(), operation.getValue()));
                }
            }
        }
        return pathOperations;
    }

    private void buildPathsTitle(String title) {
        this.markupDocBuilder.sectionTitleWithAnchorLevel1(title, PATHS_ANCHOR);
    }

    /**
     * Apply extension context to all OperationsContentExtension.
     *
     * @param context context
     */
    private void applyPathsDocumentExtension(Context context) {
        for (PathsDocumentExtension extension : extensionRegistry.getPathsDocumentExtensions()) {
            extension.apply(context);
        }
    }

    /**
     * Create the operation filename depending on the generation mode
     *
     * @param operation operation
     * @return operation filename
     */
    private String resolveOperationDocument(PathOperation operation) {
        if (config.isSeparatedOperationsEnabled())
            return new File(config.getSeparatedOperationsFolder(), this.markupDocBuilder.addFileExtension(normalizeName(operation.getId()))).getPath();
        else
            return this.markupDocBuilder.addFileExtension(config.getPathsDocument());
    }

    /**
     * Builds a path operation depending on generation mode.
     *
     * @param operation operation
     */
    private void buildOperation(PathOperation operation) {
        if (config.isSeparatedOperationsEnabled()) {
            MarkupDocBuilder pathDocBuilder = copyMarkupDocBuilder();
            buildOperation(operation, pathDocBuilder);
            java.nio.file.Path operationFile = outputPath.resolve(resolveOperationDocument(operation));
            pathDocBuilder.writeToFileWithoutExtension(operationFile, StandardCharsets.UTF_8);
            if (logger.isInfoEnabled()) {
                logger.info("Separate operation file produced : '{}'", operationFile);
            }

            buildOperationRef(operation, this.markupDocBuilder);

        } else {
            buildOperation(operation, this.markupDocBuilder);
        }

        if (logger.isInfoEnabled()) {
            logger.info("Operation processed : '{}' (normalized id = '{}')", operation, normalizeName(operation.getId()));
        }
    }

    /**
     * Builds a path operation.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildOperation(PathOperation operation, MarkupDocBuilder docBuilder) {
        if (operation != null) {
            applyPathsDocumentExtension(new Context(Position.OPERATION_BEFORE, docBuilder, operation));
            buildDeprecatedSection(operation, docBuilder);
            buildOperationTitle(operation, docBuilder);
            applyPathsDocumentExtension(new Context(Position.OPERATION_BEGIN, docBuilder, operation));
            buildDescriptionSection(operation, docBuilder);
            inlineDefinitions(buildParametersSection(operation, docBuilder), operation.getPath() + " " + operation.getMethod(), docBuilder);
            inlineDefinitions(buildBodyParameterSection(operation, docBuilder), operation.getPath() + " " + operation.getMethod(), docBuilder);
            buildExamplesResponseSection(operation,docBuilder);
            inlineDefinitions(buildResponsesSection(operation, docBuilder), operation.getPath() + " " + operation.getMethod(), docBuilder);
//            buildConsumesSection(operation, docBuilder);
//            buildProducesSection(operation, docBuilder);
//            buildTagsSection(operation, docBuilder);
//            if (config.isPathSecuritySectionEnabled()) {
//            	buildSecuritySchemeSection(operation, docBuilder);
//            }
//          buildExamplesSection(operation, docBuilder);
            applyPathsDocumentExtension(new Context(Position.OPERATION_END, docBuilder, operation));
            applyPathsDocumentExtension(new Context(Position.OPERATION_AFTER, docBuilder, operation));
        }
    }

    /**
     * Builds a cross-reference to a separated operation file
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildOperationRef(PathOperation operation, MarkupDocBuilder docBuilder) {
        String document;
        if (!config.isInterDocumentCrossReferencesEnabled() || outputPath == null)
            document = null;
        else if (config.isSeparatedOperationsEnabled())
            document = defaultString(config.getInterDocumentCrossReferencesPrefix()) + resolveOperationDocument(operation);
        else
            document = defaultString(config.getInterDocumentCrossReferencesPrefix()) + resolveOperationDocument(operation);

        buildOperationTitle(copyMarkupDocBuilder().crossReference(document, operation.getId(), operation.getTitle()).toString(), "ref-" + operation.getId(), docBuilder);
    }

    /**
     * Builds a warning if method is deprecated.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildDeprecatedSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        Boolean deprecated = operation.getOperation().isDeprecated();
        if (deprecated != null && deprecated) {
            docBuilder.block(DEPRECATED_OPERATION, MarkupBlockStyle.EXAMPLE, null, MarkupAdmonition.CAUTION);
        }
    }

    /**
     * Adds the operation title to the document. If the operation has a summary, the title is the summary.
     * Otherwise the title is the method of the operation and the URL of the operation.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildOperationTitle(PathOperation operation, MarkupDocBuilder docBuilder) {
        buildOperationTitle(operation.getTitle(), operation.getId(), docBuilder);
    }


    /**
     * Adds a operation title to the document.
     *
     * @param title      the operation title
     * @param anchor     optional anchor (null => auto-generate from title)
     * @param docBuilder the MarkupDocBuilder to use
     */
    private void buildOperationTitle(String title, String anchor, MarkupDocBuilder docBuilder) {
        if (config.getPathsGroupedBy() == GroupBy.AS_IS) {
            docBuilder.sectionTitleWithAnchorLevel1(title, anchor);
        } else {
            docBuilder.sectionTitleWithAnchorLevel1(title, anchor);
        }
    }

    /**
     * Adds a operation section title to the document.
     *
     * @param title      the section title
     * @param docBuilder the MarkupDocBuilder to use
     */
    private void buildSectionTitle(String title, MarkupDocBuilder docBuilder) {
        if (config.getPathsGroupedBy() == GroupBy.AS_IS) {
            docBuilder.sectionTitleLevel3(title);
        } else {
            docBuilder.sectionTitleLevel2(title);
        }
    }

    private void buildSubSectionTitle(String title, MarkupDocBuilder docBuilder) {
        if (config.getPathsGroupedBy() == GroupBy.AS_IS) {
            docBuilder.sectionTitleLevel4(title);
        } else {
            docBuilder.sectionTitleLevel3(title);
        }
    }

    /**
     * Adds a example title to the document.
     *
     * @param title      the section title
     * @param docBuilder the MarkupDocBuilder to use
     */
    private void buildExampleTitle(String title, MarkupDocBuilder docBuilder) {
        if (config.getPathsGroupedBy() == GroupBy.AS_IS) {
            docBuilder.sectionTitleLevel4(title);
        } else {
            docBuilder.sectionTitleLevel5(title);
        }
    }

    /**
     * Adds a operation description to the document.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildDescriptionSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        MarkupDocBuilder descriptionBuilder = copyMarkupDocBuilder();
        applyPathsDocumentExtension(new Context(Position.OPERATION_DESCRIPTION_BEGIN, descriptionBuilder, operation));
        buildDescriptionParagraph(operation.getOperation().getDescription(), descriptionBuilder);
        applyPathsDocumentExtension(new Context(Position.OPERATION_DESCRIPTION_END, descriptionBuilder, operation));
        String descriptionContent = descriptionBuilder.toString();

        applyPathsDocumentExtension(new Context(Position.OPERATION_DESCRIPTION_BEFORE, docBuilder, operation));
        if (isNotBlank(descriptionContent)) {
//            buildSectionTitle(DESCRIPTION, docBuilder);
            docBuilder.text(descriptionContent);
        }
        if (operation.getTitle().equals(operation.getOperation().getSummary())) {
            docBuilder.textLine(EXAMPLE_REQUEST_SLATE);
            buildExamplesCurlSectionInfo(operation,docBuilder);
            buildPath(operation.getMethod().toString(),operation.getPath(),docBuilder);
        }
        applyPathsDocumentExtension(new Context(Position.OPERATION_DESCRIPTION_AFTER, docBuilder, operation));
    }

    /**
     * Adds path operation title to the document.
     *
     * @param method      the path method
     * @param path        path to the ressources
     * @param docBuilder the MarkupDocBuilder to use
     */
    private void buildPath(String method, String path, MarkupDocBuilder docBuilder) {
        List<Scheme> schemes = globalContext.getSwagger().getSchemes();
//        if (schemes != null ){
//            docBuilder.block(method + " " + schemes.get(0).toString().toLowerCase() + "://"+ globalContext.getSwagger().getHost() + path, MarkupBlockStyle.LITERAL );
//        }
        buildSectionTitle(REQUEST_HEADER, docBuilder);
        for (int i=0; i<  schemes.size(); i++) {
            docBuilder.textLine("`" +method + " " + schemes.get(i).toString().toLowerCase() + "://"+ globalContext.getSwagger().getHost() + path + "`");
            docBuilder.textLine(" ");
        }
    }


    /**
     * Filter parameters to display in parameters section
     *
     * @param parameter parameter to filter
     * @return true if parameter can be displayed
     */
    private boolean filterParameter(Parameter parameter) {
        return (!config.isFlatBodyEnabled() || !StringUtils.equals(parameter.getIn(), "body"));
    }

    private List<ObjectType> buildParametersSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        List<Parameter> parameters = operation.getOperation().getParameters();
        if (config.getParameterOrdering() != null)
            Collections.sort(parameters, config.getParameterOrdering());
        List<ObjectType> inlineDefinitions = new ArrayList<>();

        boolean hasParameters = false;
        if (CollectionUtils.isNotEmpty(parameters)) {
            for (Parameter p : parameters) {
                if (filterParameter(p)) {
                    hasParameters = true;
                    break;
                }
            }
        }

        MarkupDocBuilder parametersBuilder = copyMarkupDocBuilder();
        applyPathsDocumentExtension(new Context(Position.OPERATION_DESCRIPTION_BEGIN, parametersBuilder, operation));
        if (hasParameters) {
            List<List<String>> cells = new ArrayList<>();
            List<MarkupTableColumn> cols = Arrays.asList(
                    new MarkupTableColumn(TYPE_COLUMN).withWidthRatio(2).withHeaderColumn(false).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^2"),
                    new MarkupTableColumn(NAME_COLUMN).withWidthRatio(3).withHeaderColumn(false).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^3"),
                    new MarkupTableColumn(DESCRIPTION_COLUMN).withWidthRatio(9).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^9"),
                    new MarkupTableColumn(SCHEMA_COLUMN).withWidthRatio(4).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^4"),
                    new MarkupTableColumn(DEFAULT_COLUMN).withWidthRatio(2).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^2"));
            for (Parameter parameter : parameters) {
                if (filterParameter(parameter)) {
                    Type type = ParameterUtils.getType(parameter, globalContext.getSwagger().getDefinitions(), new DefinitionDocumentResolverFromOperation());

                    type = createInlineType(type, parameter.getName(), operation.getId() + " " + parameter.getName(), inlineDefinitions);

                    String parameterType = WordUtils.capitalize(parameter.getIn());

                    MarkupDocBuilder parameterNameContent = copyMarkupDocBuilder();
                    parameterNameContent.boldTextLine(parameter.getName(), true);
                    if (parameter.getRequired())
                        parameterNameContent.italicText(FLAGS_REQUIRED.toLowerCase());
                    else
                        parameterNameContent.italicText(FLAGS_OPTIONAL.toLowerCase());

                    Object defaultValue = ParameterUtils.getDefaultValue(parameter);

                    List<String> content = Arrays.asList(
                            boldText(parameterType),
                            parameterNameContent.toString(),
                            defaultString(swaggerMarkupDescription(parameter.getDescription())),
                            type.displaySchema(markupDocBuilder),
                            defaultValue != null ? literalText(Json.pretty(defaultValue)) : "");
                    cells.add(content);
                }
            }
            parametersBuilder.tableWithColumnSpecs(cols, cells);
        }
        applyPathsDocumentExtension(new Context(Position.OPERATION_DESCRIPTION_END, parametersBuilder, operation));
        String parametersContent = parametersBuilder.toString();

        applyPathsDocumentExtension(new Context(Position.OPERATION_PARAMETERS_BEFORE, docBuilder, operation));
        if (isNotBlank(parametersContent)) {
            buildSectionTitle(ARGUMENTS, docBuilder);
            docBuilder.text(parametersContent);
        }
        applyPathsDocumentExtension(new Context(Position.OPERATION_PARAMETERS_AFTER, docBuilder, operation));

        return inlineDefinitions;
    }

    /**
     * Builds the body parameter section, if {@code Swagger2MarkupConfig.isIsolatedBody()} is true
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     * @return a list of inlined types.
     */
    private List<ObjectType> buildBodyParameterSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        List<ObjectType> inlineDefinitions = new ArrayList<>();

        if (config.isFlatBodyEnabled()) {
            List<Parameter> parameters = operation.getOperation().getParameters();
            if (CollectionUtils.isNotEmpty(parameters)) {
                for (Parameter parameter : parameters) {
                    if (StringUtils.equals(parameter.getIn(), "body")) {
                        Type type = ParameterUtils.getType(parameter, globalContext.getSwagger().getDefinitions(), new DefinitionDocumentResolverFromOperation());

                        if (!(type instanceof ObjectType)) {
                            type = createInlineType(type, parameter.getName(), operation.getId() + " " + parameter.getName(), inlineDefinitions);
                        }

                        buildSectionTitle(BODY_PARAMETER, docBuilder);
                        if (isNotBlank(parameter.getDescription())) {
                            buildDescriptionParagraph(parameter.getDescription(), docBuilder);
                        }

                        MarkupDocBuilder typeInfos = copyMarkupDocBuilder();
                        typeInfos.italicText(NAME_COLUMN).textLine(COLON + parameter.getName());
                        typeInfos.italicText(FLAGS_COLUMN).textLine(COLON + (BooleanUtils.isTrue(parameter.getRequired()) ? FLAGS_REQUIRED.toLowerCase() : FLAGS_OPTIONAL.toLowerCase()));

                        if (!(type instanceof ObjectType)) {
                            typeInfos.italicText(TYPE_COLUMN).textLine(COLON + type.displaySchema(docBuilder));
                        }

                        docBuilder.paragraph(typeInfos.toString(), true);

                        if (type instanceof ObjectType) {
                            inlineDefinitions.addAll(buildPropertiesTable(((ObjectType) type).getProperties(), operation.getId(), new DefinitionDocumentResolverFromOperation(), docBuilder));
                        }
                    }
                }
            }
        }

        return inlineDefinitions;
    }

    private void buildConsumesSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        List<String> consumes = operation.getOperation().getConsumes();
        if (CollectionUtils.isNotEmpty(consumes)) {
            buildSectionTitle(CONSUMES, docBuilder);
            docBuilder.newLine();
            for (String consume : consumes) {
                docBuilder.unorderedListItem(literalText(consume));
            }
            docBuilder.newLine();
        }

    }

    private void buildProducesSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        List<String> produces = operation.getOperation().getProduces();
        if (CollectionUtils.isNotEmpty(produces)) {
            buildSectionTitle(PRODUCES, docBuilder);
            docBuilder.newLine();
            for (String produce : produces) {
                docBuilder.unorderedListItem(literalText(produce));
            }
            docBuilder.newLine();
        }
    }

    private void buildTagsSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        if (config.getPathsGroupedBy() == GroupBy.AS_IS) {
            List<String> tags = operation.getOperation().getTags();
            if (CollectionUtils.isNotEmpty(tags)) {
                buildSectionTitle(TAGS, docBuilder);
                Set<String> tagsSet = toSet(tags, config.getTagOrdering());
                docBuilder.unorderedList(new ArrayList<>(tagsSet));
            }
        }
    }


    /**
     * Builds the GET curl example section of a Swagger Operation.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildExamplesCurlSectionInfo(PathOperation operation, MarkupDocBuilder docBuilder) {
        String curlValue = "curl \"" + basePath  + operation.getPath()  ;
        String hederValue = "";
        String bodyValue = "";
        String queryValue = "";
        List<Parameter> parameters = operation.getOperation().getParameters();
        for (int i=0; i< parameters.size(); i++ ) {
            if ( parameters.get(i).getRequired() ) {
                String value = null;
                if (parameters.get(i).getDescription() != null) {
                    value = getExampleFromDescription(parameters.get(i).getDescription());
                }
                if (parameters.get(i).getIn().equals(BODY)) {
                    BodyParameter bodyParameter = (BodyParameter)parameters.get(i);
                    if (bodyParameter.getSchema() != null ) {
                        RefModel ref = (RefModel) bodyParameter.getSchema();
                        value = Json.pretty(ExamplesUtil.generateExampleForRefModel(true, ref.getSimpleRef(),globalContext.getSwagger().getDefinitions(), docBuilder)).replace("\n", " \\ \n");
                    }
                }
                if (value == null) {
                    continue;
                }

                if (parameters.get(i).getIn().equals(PATH)) {
                    curlValue = curlValue.replace("{" + parameters.get(i).getName() + "}", value);
                    continue;
                }
                if (parameters.get(i).getIn().equals(HEADER) ) {
                    if (!hederValue.equals("")) {
                        hederValue+= "\\ \n";
                    }
                    hederValue += "-H \"" + parameters.get(i).getName()+":" + value +" \"" ;
                    continue;
                }

                if (parameters.get(i).getIn().equals(QUERY)) {
                    if (queryValue == null ) {
                        queryValue +=":";
                    }else {
                        queryValue +="&";
                    }
                    queryValue +=  parameters.get(i).getName() + ":" + value;
                    continue;
                }
                if (parameters.get(i).getIn().equals(BODY) ) {
                    if (!bodyValue.equals("")) {
                        bodyValue+= "\\ \n";
                    }
                    bodyValue += "-d '" + value +"'";
                    continue;
                }
            }

        }
        if (queryValue != null) {
            curlValue += queryValue ;
        }
        curlValue += "\"";
        if (!hederValue.equals("")) {
            curlValue += " \\ \n";
            curlValue += hederValue ;
        }
        if (!bodyValue.equals("")) {
            curlValue += " \\ \n";
            curlValue += bodyValue;
        }
        if (operation.getMethod() != HttpMethod.GET) {
            curlValue += " \\ \n";
            curlValue += " -X " + operation.getMethod();
        }
        generateLanguageBlock(curlValue, "shell", docBuilder);
    }

    private String getExampleFromDescription(String description) {
        String[] info = description.split("\\*\\*Example:\\*\\* ");
        if (info.length >1) {
           return info[1];
        } else {
            return null;
        }
    }


    /**
     * Builds the example response section of a Swagger Operation.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildExamplesResponseSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        Map<String, Object> generatedResponseExampleMap = ExamplesUtil.generateResponseExampleMap(config.isGeneratedExamplesEnabled(), operation.getOperation(), globalContext.getSwagger().getDefinitions(), markupDocBuilder);
        exampleMap(generatedResponseExampleMap, EXAMPLE_RESPONSE_SLATE, RESPONSE, docBuilder);
    }

    /**
     * Builds the example section of a Swagger Operation.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the docbuilder do use for output
     */
    private void buildExamplesSection(PathOperation operation, MarkupDocBuilder docBuilder) {

        Map<String, Object> generatedRequestExampleMap = ExamplesUtil.generateRequestExampleMap(config.isGeneratedExamplesEnabled(), operation, globalContext.getSwagger().getDefinitions(), markupDocBuilder);
        Map<String, Object> generatedResponseExampleMap = ExamplesUtil.generateResponseExampleMap(config.isGeneratedExamplesEnabled(), operation.getOperation(), globalContext.getSwagger().getDefinitions(), markupDocBuilder);

        exampleMap(generatedRequestExampleMap, EXAMPLE_REQUEST_SLATE, REQUEST, docBuilder);
        exampleMap(generatedResponseExampleMap, EXAMPLE_RESPONSE_SLATE, RESPONSE, docBuilder);
    }

    private void exampleMap(Map<String, Object> exampleMap, String operationSectionTitle, String sectionTitle, MarkupDocBuilder docBuilder) {
        if (exampleMap.size() > 0) {
            docBuilder.textLine(operationSectionTitle);
            for (Map.Entry<String, Object> entry : exampleMap.entrySet()) {
//                buildExampleTitle(sectionTitle + " " + entry.getKey(), docBuilder);
                generateLanguageBlock(Json.pretty(entry.getValue()), "json", docBuilder);
            }
        }
    }

    private void generateLanguageBlock(String code, String codeLanguage, MarkupDocBuilder docBuilder) {
        docBuilder.textLine("```" + codeLanguage);
        docBuilder.textLine(code);
        docBuilder.textLine("```");

    }


    /**
     * Builds the security section of a Swagger Operation.
     *
     * @param operation  the Swagger Operation
     * @param docBuilder the MarkupDocBuilder document builder
     */
    private void buildSecuritySchemeSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        List<Map<String, List<String>>> securitySchemes = operation.getOperation().getSecurity();

        MarkupDocBuilder securityBuilder = copyMarkupDocBuilder();
        applyPathsDocumentExtension(new Context(Position.OPERATION_SECURITY_BEGIN, securityBuilder, operation));
        if (CollectionUtils.isNotEmpty(securitySchemes)) {

            Map<String, SecuritySchemeDefinition> securityDefinitions = globalContext.getSwagger().getSecurityDefinitions();
            List<List<String>> cells = new ArrayList<>();
            List<MarkupTableColumn> cols = Arrays.asList(
                    new MarkupTableColumn(TYPE_COLUMN).withWidthRatio(3).withHeaderColumn(false).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^3"),
                    new MarkupTableColumn(NAME_COLUMN).withWidthRatio(4).withHeaderColumn(false).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^4"),
                    new MarkupTableColumn(SCOPES_COLUMN).withWidthRatio(13).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^13"));
            for (Map<String, List<String>> securityScheme : securitySchemes) {
                for (Map.Entry<String, List<String>> securityEntry : securityScheme.entrySet()) {
                    String securityKey = securityEntry.getKey();
                    String type = UNKNOWN;
                    if (securityDefinitions != null && securityDefinitions.containsKey(securityKey)) {
                        type = securityDefinitions.get(securityKey).getType();
                    }

                    List<String> content = Arrays.asList(boldText(type), boldText(copyMarkupDocBuilder().crossReference(securityDocumentResolver(), securityKey, securityKey).toString()),
                            Joiner.on(",").join(securityEntry.getValue()));
                    cells.add(content);
                }
            }
            securityBuilder.tableWithColumnSpecs(cols, cells);
        }
        applyPathsDocumentExtension(new Context(Position.OPERATION_SECURITY_END, securityBuilder, operation));
        String securityContent = securityBuilder.toString();

        applyPathsDocumentExtension(new Context(Position.OPERATION_SECURITY_BEFORE, docBuilder, operation));
        if (isNotBlank(securityContent)) {
            buildSectionTitle(SECURITY, docBuilder);
            docBuilder.text(securityContent);
        }
        applyPathsDocumentExtension(new Context(Position.OPERATION_SECURITY_AFTER, docBuilder, operation));
    }

    /**
     * Resolve Security document for use in cross-references.
     *
     * @return document or null if cross-reference is not inter-document
     */
    private String securityDocumentResolver() {
        if (!config.isInterDocumentCrossReferencesEnabled() || outputPath == null)
            return null;
        else
            return defaultString(config.getInterDocumentCrossReferencesPrefix()) + markupDocBuilder.addFileExtension(config.getSecurityDocument());
    }

    private List<ObjectType> buildResponsesSection(PathOperation operation, MarkupDocBuilder docBuilder) {
        Map<String, Response> responses = operation.getOperation().getResponses();
        List<ObjectType> inlineDefinitions = new ArrayList<>();

        MarkupDocBuilder responsesBuilder = copyMarkupDocBuilder();
        applyPathsDocumentExtension(new Context(Position.OPERATION_RESPONSES_BEGIN, responsesBuilder, operation));
        if (MapUtils.isNotEmpty(responses)) {
            List<MarkupTableColumn> responseCols = Arrays.asList(
                    new MarkupTableColumn(HTTP_CODE_COLUMN).withWidthRatio(2).withHeaderColumn(false).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^2"),
                    new MarkupTableColumn(DESCRIPTION_COLUMN).withWidthRatio(14).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^14"),
                    new MarkupTableColumn(SCHEMA_COLUMN).withWidthRatio(4).withMarkupSpecifiers(MarkupLanguage.ASCIIDOC, ".^4"));

            List<List<String>> cells = new ArrayList<>();

            Set<String> responseNames = toKeySet(responses, config.getResponseOrdering());
            for (String responseName : responseNames) {
                Response response = responses.get(responseName);

                String schemaContent = NO_CONTENT;
                if (response.getSchema() != null) {
                    Property property = response.getSchema();
                    Type type = PropertyUtils.getType(property, new DefinitionDocumentResolverFromOperation());

                    type = createInlineType(type, RESPONSE + " " + responseName, operation.getId() + " " + RESPONSE + " " + responseName, inlineDefinitions);

                    schemaContent = type.displaySchema(markupDocBuilder);
                }

                MarkupDocBuilder descriptionBuilder = copyMarkupDocBuilder();

                descriptionBuilder.text(defaultString(swaggerMarkupDescription(response.getDescription())));

                Map<String, Property> headers = response.getHeaders();
                if (MapUtils.isNotEmpty(headers)) {
                    descriptionBuilder.newLine(true).boldText(HEADERS_COLUMN).text(COLON);
                    for (Map.Entry<String, Property> header : headers.entrySet()) {
                        descriptionBuilder.newLine(true);
                        Property headerProperty = header.getValue();
                        Type propertyType = PropertyUtils.getType(headerProperty, null);
                        String headerDescription = defaultString(swaggerMarkupDescription(headerProperty.getDescription()));
                        Object defaultValue = PropertyUtils.getDefaultValue(headerProperty);

                        descriptionBuilder
                                .literalText(header.getKey())
                                .text(String.format(" (%s)", propertyType.displaySchema(markupDocBuilder)));

                        if (isNotBlank(headerDescription) || defaultValue != null) {
                            descriptionBuilder.text(COLON);

                            if (isNotBlank(headerDescription) && !headerDescription.endsWith("."))
                                headerDescription += ".";

                            descriptionBuilder.text(headerDescription);

                            if (defaultValue != null) {
                                descriptionBuilder.text(" ").boldText(DEFAULT_COLUMN).text(COLON).literalText(Json.pretty(defaultValue));
                            }
                        }
                    }
                }

                cells.add(Arrays.asList(boldText(responseName), descriptionBuilder.toString(), schemaContent));
            }

            responsesBuilder.tableWithColumnSpecs(responseCols, cells);
        }
        applyPathsDocumentExtension(new Context(Position.OPERATION_RESPONSES_END, responsesBuilder, operation));
        String responsesContent = responsesBuilder.toString();

        applyPathsDocumentExtension(new Context(Position.OPERATION_RESPONSES_BEFORE, docBuilder, operation));
        if (isNotBlank(responsesContent)) {
            buildSectionTitle(RESPONSES, docBuilder);
            docBuilder.text(responsesContent);
        }
        applyPathsDocumentExtension(new Context(Position.OPERATION_RESPONSES_AFTER, docBuilder, operation));

        return inlineDefinitions;
    }

    /**
     * Builds the title of an inline schema.
     * Inline definitions should never been referenced in TOC because they have no real existence, so they are just text.
     *
     * @param title      inline schema title
     * @param anchor     inline schema anchor
     * @param docBuilder the docbuilder do use for output
     */
    private void addInlineDefinitionTitle(String title, String anchor, MarkupDocBuilder docBuilder) {
        docBuilder.anchor(anchor);
        docBuilder.newLine();
        docBuilder.boldTextLine(title);
    }

    /**
     * Builds inline schema definitions
     *
     * @param definitions  all inline definitions to display
     * @param uniquePrefix unique prefix to prepend to inline object names to enforce unicity
     * @param docBuilder   the docbuilder do use for output
     */
    private void inlineDefinitions(List<ObjectType> definitions, String uniquePrefix, MarkupDocBuilder docBuilder) {
        if (CollectionUtils.isNotEmpty(definitions)) {
            for (ObjectType definition : definitions) {
                addInlineDefinitionTitle(definition.getName(), definition.getUniqueName(), docBuilder);

                List<ObjectType> localDefinitions = buildPropertiesTable(definition.getProperties(), uniquePrefix, new DefinitionDocumentResolverFromOperation(), docBuilder);
                for (ObjectType localDefinition : localDefinitions)
                    inlineDefinitions(Collections.singletonList(localDefinition), localDefinition.getUniqueName(), docBuilder);
            }
        }

    }

    /**
     * Overrides definition document resolver functor for inter-document cross-references from operations files.
     * This implementation adapt the relative paths to definitions files
     */
    class DefinitionDocumentResolverFromOperation extends DefinitionDocumentResolverDefault {

        public DefinitionDocumentResolverFromOperation() {
        }

        public String apply(String definitionName) {
            String defaultResolver = super.apply(definitionName);

            if (defaultResolver != null && config.isSeparatedOperationsEnabled())
                return defaultString(config.getInterDocumentCrossReferencesPrefix()) + new File("..", defaultResolver).getPath();
            else
                return defaultResolver;
        }
    }
}
