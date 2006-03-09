/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
package com.sun.xml.stream.buffer;

import com.sun.xml.stream.buffer.sax.Properties;
import com.sun.xml.stream.buffer.sax.SAXBufferCreator;
import com.sun.xml.stream.buffer.sax.SAXBufferProcessor;
import com.sun.xml.stream.buffer.stax.StreamReaderBufferCreator;
import com.sun.xml.stream.buffer.stax.StreamReaderBufferProcessor;
import com.sun.xml.stream.buffer.stax.StreamWriterBufferCreator;
import com.sun.xml.stream.buffer.stax.StreamWriterBufferProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 *
 * A mutable stream-based buffer of an XML infoset.
 *
 * <p>
 * A XMLStreamBuffer is created using specific SAX and StAX-based 
 * creators. Utility methods on XMLStreamBuffer are provided for 
 * such functionality that utilize SAX and StAX-based creators.
 *
 * <p>
 * Once instantiated the same instance of a XMLStreamBuffer may be reused for
 * creation to reduce the amount of Objects instantiated and garbage
 * collected that are required for internally representing an XML infoset.
 *
 * <p>
 * A XMLStreamBuffer is not designed to be created and processed 
 * concurrently. If done so unspecified behaviour may occur.
 */
public class XMLStreamBuffer extends ImmutableXMLStreamBuffer {
    /**
     * The default array size for the arrays used in internal representation 
     * of the XML infoset.
     */
    public static int DEFAULT_ARRAY_SIZE = 512;
    
    /**
     * Create a new XMLStreamBuffer using the 
     * {@link XMLStreamBuffer#DEFAULT_ARRAY_SIZE}.
     */
    public XMLStreamBuffer() {
        this(DEFAULT_ARRAY_SIZE);
    }
    
    /**
     * Create a new XMLStreamBuffer.
     *
     * @throws NegativeArraySizeException
     * If the <code>size</code> argument is less than <code>0</code>.
     *
     * @param size
     * The size of the arrays used in the internal representation 
     * of the XML infoset. 
     */
    public XMLStreamBuffer(int size) {
        _structure = new FragmentedArray(new int[size]);
        _structureStrings = new FragmentedArray(new String[size]);
        _contentCharactersBuffer = new FragmentedArray(new char[4096]);
        _contentObjects = new FragmentedArray(new Object[size]);

        // Set the first element of structure array to indicate an empty buffer
        // that has not been created
        _structure.getArray()[0] = AbstractCreatorProcessor.T_END;
    }

    /**
     * Create contents of a buffer from a XMLStreamReader.
     *
     * <p>
     * The XMLStreamBuffer is reset (see {@link #reset}) before creation.
     *
     * <p>
     * The XMLStreamBuffer is created by consuming the events on the XMLStreamReader using
     * an instance of {@link StreamReaderBufferCreator}.
     *
     * @param reader
     * A XMLStreamReader to read from to create.
     */
    public void createFromXMLStreamReader(XMLStreamReader reader) throws XMLStreamException, XMLStreamBufferException {
        reset();
        StreamReaderBufferCreator c = new StreamReaderBufferCreator(this);
        c.create(reader);
    }

    /**
     * Create contents of a buffer from a XMLStreamWriter.
     *
     * <p>
     * The XMLStreamBuffer is reset (see {@link #reset}) before creation.
     *
     * <p>
     * The XMLStreamBuffer is created by consuming events on a XMLStreamWriter using
     * an instance of {@link StreamWriterBufferCreator}.
     */
    public XMLStreamWriter createFromXMLStreamWriter() throws XMLStreamBufferException {
        reset();
        return new StreamWriterBufferCreator(this);
    }

    /**
     * Create contents of a buffer from a {@link SAXBufferCreator}.
     *
     * <p>
     * The XMLStreamBuffer is reset (see {@link #reset}) before creation.
     *
     * <p>
     * The XMLStreamBuffer is created by consuming events from a {@link ContentHandler} using
     * an instance of {@link SAXBufferCreator}.
     *
     * @return
     * The {@link SAXBufferCreator} to create from.
     */
    public SAXBufferCreator createFromSAXBufferCreator() throws XMLStreamBufferException {
        reset();
        SAXBufferCreator c = new SAXBufferCreator();
        c.setBuffer(this);
        return c;
    }

    /**
     * Create contents of a buffer from a {@link XMLReader} and {@link InputStream}.
     *
     * <p>
     * The XMLStreamBuffer is reset (see {@link #reset}) before creation.
     *
     * <p>
     * The XMLStreamBuffer is created by using an instance of {@link SAXBufferCreator}
     * and registering associated handlers on the {@link XMLReader}.
     *
     * @param reader
     * The {@link XMLReader} to use for parsing.
     * @param in
     * The {@link InputStream} to be parsed.
     */
    public void createFromXMLReader(XMLReader reader, InputStream in) throws XMLStreamBufferException, SAXException, IOException {
        reset();
        SAXBufferCreator c = new SAXBufferCreator(this);

        reader.setContentHandler(c);
        reader.setDTDHandler(c);
        reader.setProperty(Properties.LEXICAL_HANDLER_PROPERTY, c);

        c.create(reader, in);
    }

    /**
     * Reset the XMLStreamBuffer.
     *
     * <p>
     * This method will reset the XMLStreamBuffer to a state of being "uncreated"
     * similar to the state of a newly instantiated XMLStreamBuffer.
     *
     * <p>
     * As many Objects as possible will be retained for reuse in future creation.
     *
     */
    public void reset() {
        // Reset the ptrs in arrays to 0
        _structurePtr =
                _structureStringsPtr =
                _contentCharactersBufferPtr = 
                _contentObjectsPtr = 0;

        // Set the first element of structure array to indicate an empty buffer
        // that has not been created
        _structure.getArray()[0] = AbstractCreatorProcessor.T_END;

        // Clean up content objects
        _contentObjects.setNext(null);
        final Object[] o = _contentObjects.getArray();
        for (int i = 0; i < o.length; i++) {
            if (o[i] != null) {
                o[i] = null;
            } else {
                break;
            }
        }
        
        /*
         * TODO consider truncating the size of _structureStrings and
         * _contentCharactersBuffer to limit the memory used by the buffer
         */
    }
    
    
    protected void setHasInternedStrings(boolean hasInternedStrings) {
        _hasInternedStrings = hasInternedStrings;
    }
}