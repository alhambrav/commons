/*
 * Copyright (C) 2007-2014 Crafter Software Corporation.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.craftercms.commons.rest;

import org.craftercms.commons.i10n.I10nLogger;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.context.request.ServletWebRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * Writes the response using a {@link org.springframework.http.converter.HttpMessageConverter} chosen depending on
 * the acceptable media types from the request (most of the code is just a copy from Spring's {@code
 * AbstractMessageConverterMethodProcessor}).
 *
 * @author avasquez
 */
public class HttpMessageConvertingResponseWriter {

    private static final I10nLogger logger = new I10nLogger(HttpMessageConvertingResponseWriter.class,
            "crafter.commons.messages.logging");

    public static final MediaType MEDIA_TYPE_APPLICATION = new MediaType("application");

    public static final String LOG_KEY_WRITTEN_WITH_MESSAGE_CONVERTER = "logging.rest.writtenWithMessageConverter";

    protected ContentNegotiationManager contentNegotiationManager;
    protected List<HttpMessageConverter<?>> messageConverters;
    protected List<MediaType> allSupportedMediaTypes;

    public HttpMessageConvertingResponseWriter(ContentNegotiationManager contentNegotiationManager,
                                               List<HttpMessageConverter<?>> messageConverters) {
        this.contentNegotiationManager = contentNegotiationManager;
        this.messageConverters = messageConverters;
        this.allSupportedMediaTypes = getAllSupportedMediaTypes(messageConverters);
    }

    public <T> void writeWithMessageConverters(T returnValue, HttpServletRequest request, HttpServletResponse response)
            throws IOException, HttpMediaTypeNotAcceptableException {
        Class<?> returnValueClass = returnValue.getClass();
        List<MediaType> requestedMediaTypes = getAcceptableMediaTypes(request);
        List<MediaType> producibleMediaTypes = getProducibleMediaTypes(returnValueClass);

        Set<MediaType> compatibleMediaTypes = new LinkedHashSet<MediaType>();
        for (MediaType r : requestedMediaTypes) {
            for (MediaType p : producibleMediaTypes) {
                if (r.isCompatibleWith(p)) {
                    compatibleMediaTypes.add(getMostSpecificMediaType(r, p));
                }
            }
        }
        if (compatibleMediaTypes.isEmpty()) {
            throw new HttpMediaTypeNotAcceptableException(producibleMediaTypes);
        }

        List<MediaType> mediaTypes = new ArrayList<MediaType>(compatibleMediaTypes);
        MediaType.sortBySpecificityAndQuality(mediaTypes);

        MediaType selectedMediaType = null;
        for (MediaType mediaType : mediaTypes) {
            if (mediaType.isConcrete()) {
                selectedMediaType = mediaType;
                break;
            } else if (mediaType.equals(MediaType.ALL) || mediaType.equals(MEDIA_TYPE_APPLICATION)) {
                selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
                break;
            }
        }

        if (selectedMediaType != null) {
            selectedMediaType = selectedMediaType.removeQualityValue();
            for (HttpMessageConverter<?> messageConverter : messageConverters) {
                if (messageConverter.canWrite(returnValueClass, selectedMediaType)) {
                    ((HttpMessageConverter<T>) messageConverter).write(returnValue, selectedMediaType,
                            new ServletServerHttpResponse(response));

                    logger.debug(LOG_KEY_WRITTEN_WITH_MESSAGE_CONVERTER, returnValue, selectedMediaType,
                            messageConverter);

                    return;
                }
            }
        }

        throw new HttpMediaTypeNotAcceptableException(allSupportedMediaTypes);
    }

    /**
     * Returns the media types that can be produced:
     * <ul>
     * 	<li>Media types of configured converters that can write the specific return value, or</li>
     * 	<li>{@link org.springframework.http.MediaType#ALL}</li>
     * </ul>
     */
    protected List<MediaType> getProducibleMediaTypes(Class<?> returnValueClass) {
        if (!allSupportedMediaTypes.isEmpty()) {
            List<MediaType> result = new ArrayList<>();
            for (HttpMessageConverter<?> converter : messageConverters) {
                if (converter.canWrite(returnValueClass, null)) {
                    result.addAll(converter.getSupportedMediaTypes());
                }
            }

            return result;
        }
        else {
            return Collections.singletonList(MediaType.ALL);
        }
    }

    /**
     * Return the acceptable media types from the request.
     */
    protected List<MediaType> getAcceptableMediaTypes(HttpServletRequest request)
            throws HttpMediaTypeNotAcceptableException {
        List<MediaType> mediaTypes = contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
        return mediaTypes.isEmpty() ? Collections.singletonList(MediaType.ALL) : mediaTypes;
    }

    /**
     * Return the more specific of the acceptable and the producible media types with the q-value of the former.
     */
    protected MediaType getMostSpecificMediaType(MediaType acceptType, MediaType produceType) {
        produceType = produceType.copyQualityValue(acceptType);

        return MediaType.SPECIFICITY_COMPARATOR.compare(acceptType, produceType) <= 0 ? acceptType : produceType;
    }

    /**
     * Return the media types supported by all provided message converters sorted by specificity via
     * {@link MediaType#sortBySpecificity(List)}.
     */
    protected List<MediaType> getAllSupportedMediaTypes(List<HttpMessageConverter<?>> messageConverters) {
        Set<MediaType> allSupportedMediaTypes = new LinkedHashSet<MediaType>();
        for (HttpMessageConverter<?> messageConverter : messageConverters) {
            allSupportedMediaTypes.addAll(messageConverter.getSupportedMediaTypes());
        }

        List<MediaType> result = new ArrayList<MediaType>(allSupportedMediaTypes);

        MediaType.sortBySpecificity(result);

        return Collections.unmodifiableList(result);
    }


}