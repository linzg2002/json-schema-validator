/*
 * Copyright (c) 2013, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.fge.jsonschema.processing.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonschema.SampleNodeProvider;
import com.github.fge.jsonschema.format.FormatAttribute;
import com.github.fge.jsonschema.keyword.validator.KeywordValidator;
import com.github.fge.jsonschema.library.Dictionary;
import com.github.fge.jsonschema.processing.ProcessingException;
import com.github.fge.jsonschema.processing.Processor;
import com.github.fge.jsonschema.processing.ValidationData;
import com.github.fge.jsonschema.processing.build.FullValidationContext;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.tree.CanonicalSchemaTree2;
import com.github.fge.jsonschema.tree.JsonTree2;
import com.github.fge.jsonschema.tree.SchemaTree;
import com.github.fge.jsonschema.tree.SimpleJsonTree2;
import com.github.fge.jsonschema.util.JacksonUtils;
import com.github.fge.jsonschema.util.NodeType;
import com.google.common.collect.Lists;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import static com.github.fge.jsonschema.matchers.ProcessingMessageAssert.*;
import static com.github.fge.jsonschema.messages.FormatMessages.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public final class FormatProcessorTest
{
    private static final JsonNodeFactory FACTORY = JacksonUtils.nodeFactory();
    private static final JsonTree2 TREE
        = new SimpleJsonTree2(FACTORY.nullNode());
    private static final String FMT = "fmt";
    private static final EnumSet<NodeType> SUPPORTED
        = EnumSet.of(NodeType.INTEGER, NodeType.NUMBER, NodeType.BOOLEAN);

    private FormatAttribute attribute;
    private FormatProcessor processor;
    private ProcessingReport report;

    @BeforeMethod
    public void init()
    {
        attribute = mock(FormatAttribute.class);
        when(attribute.supportedTypes()).thenReturn(SUPPORTED);
        report = mock(ProcessingReport.class);
        final Dictionary<FormatAttribute> dictionary
            = Dictionary.<FormatAttribute>newBuilder().addEntry(FMT, attribute)
                .freeze();
        processor = new FormatProcessor(dictionary);
    }

    @Test
    public void noFormatInSchemaIsANoOp()
        throws ProcessingException
    {
        final ObjectNode schema = FACTORY.objectNode();
        final SchemaTree tree = new CanonicalSchemaTree2(schema);
        final ValidationData data = new ValidationData(tree, TREE);
        final FullValidationContext in = new FullValidationContext(data,
            Collections.<KeywordValidator>emptyList());

        final FullValidationContext out = processor.process(report, in);

        assertTrue(Lists.newArrayList(out).isEmpty());

        verifyZeroInteractions(report);
    }

    @Test
    public void unknownFormatAttributesAreReportedAsWarnings()
        throws ProcessingException
    {
        final ObjectNode schema = FACTORY.objectNode();
        schema.put("format", "foo");
        final SchemaTree tree = new CanonicalSchemaTree2(schema);
        final ValidationData data = new ValidationData(tree, TREE);
        final FullValidationContext in = new FullValidationContext(data,
            Collections.<KeywordValidator>emptyList());

        final ArgumentCaptor<ProcessingMessage> captor
            = ArgumentCaptor.forClass(ProcessingMessage.class);

        final FullValidationContext out = processor.process(report, in);

        assertTrue(Lists.newArrayList(out).isEmpty());

        verify(report).warn(captor.capture());

        final ProcessingMessage message = captor.getValue();

        assertMessage(message).hasMessage(FORMAT_NOT_SUPPORTED)
            .hasField("domain", "validation").hasField("keyword", "format")
            .hasField("attribute", "foo");
    }

    @Test
    public void attributeIsBeingAskedWhatIsSupports()
        throws ProcessingException
    {
        final ObjectNode schema = FACTORY.objectNode();
        schema.put("format", FMT);
        final SchemaTree tree = new CanonicalSchemaTree2(schema);
        final ValidationData data = new ValidationData(tree, TREE);
        final FullValidationContext in = new FullValidationContext(data,
            Collections.<KeywordValidator>emptyList());

        processor.process(report, in);
        verify(attribute).supportedTypes();
    }

    @DataProvider
    public Iterator<Object[]> supported()
    {
        return SampleNodeProvider.getSamples(SUPPORTED);
    }

    @Test(
        dataProvider = "supported",
        dependsOnMethods = "attributeIsBeingAskedWhatIsSupports"
    )
    public void supportedNodeTypesTriggerAttributeBuild(final JsonNode node)
        throws ProcessingException
    {
        final ObjectNode schema = FACTORY.objectNode();
        schema.put("format", FMT);
        final SchemaTree tree = new CanonicalSchemaTree2(schema);
        final JsonTree2 instance = new SimpleJsonTree2(node);
        final ValidationData data = new ValidationData(tree, instance);
        final FullValidationContext in = new FullValidationContext(data,
            Collections.<KeywordValidator>emptyList());

        final FullValidationContext out = processor.process(report, in);

        final List<KeywordValidator> validators = Lists.newArrayList(out);

        assertEquals(validators.size(), 1);

        @SuppressWarnings("unchecked")
        final Processor<ValidationData, ProcessingReport> p
            = mock(Processor.class);

        validators.get(0).validate(p, report, data);
        verify(attribute).validate(report, data);
    }

    @DataProvider
    public Iterator<Object[]> unsupported()
    {
        return SampleNodeProvider.getSamplesExcept(SUPPORTED);
    }

    @Test(
        dataProvider = "unsupported",
        dependsOnMethods = "attributeIsBeingAskedWhatIsSupports"
    )
    public void unsupportedTypeDoesNotTriggerValidatorBuild(final JsonNode node)
        throws ProcessingException
    {
        final ObjectNode schema = FACTORY.objectNode();
        schema.put("format", FMT);
        final SchemaTree tree = new CanonicalSchemaTree2(schema);
        final JsonTree2 instance = new SimpleJsonTree2(node);
        final ValidationData data = new ValidationData(tree, instance);
        final FullValidationContext in = new FullValidationContext(data,
            Collections.<KeywordValidator>emptyList());

        final FullValidationContext out = processor.process(report, in);

        final List<KeywordValidator> validators = Lists.newArrayList(out);

        assertTrue(validators.isEmpty());
    }
}