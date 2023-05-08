/**
 * @Title: RestService.java Copyright (c) 2019 Sharp. All rights reserved.
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.service
 * @author sharp
 * @since 2019-01-23 09:37:10
 */
package com.bidr.platform.service.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;

import java.io.File;
import java.io.IOException;

/**
 * The interface Rest service.
 *
 * @author sharp
 */
public interface RestService {

    /**
     * Get t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T get(String url, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Get t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param header          the header
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T get(String url, HttpHeaders header, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Get t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param param           the param
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T get(String url, Object param, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Get t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param header          the header
     * @param param           the param
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T get(String url, HttpHeaders header, Object param, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, Object body, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, LinkedMultiValueMap body, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param header          the header
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, Object body, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param header          the header
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, LinkedMultiValueMap body, Class<?> collectionClass,
               Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param param           the param
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, Object param, Object body, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param param           the param
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, Object param, LinkedMultiValueMap body, Class<?> collectionClass,
               Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param header          the header
     * @param param           the param
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, Object param, Object body, Class<?> collectionClass,
               Class<?>... elementClasses);

    /**
     * Post t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param header          the header
     * @param param           the param
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, Object param, LinkedMultiValueMap body, Class<?> collectionClass,
               Class<?>... elementClasses);

    /**
     * Exec t.
     *
     * @param <T>             the type parameter
     * @param url             the url
     * @param method          the method
     * @param header          the header
     * @param param           the param
     * @param body            the body
     * @param collectionClass collectionClass
     * @param elementClasses  elementClasses array
     * @return the t
     */
    <T> T exec(String url, HttpMethod method, HttpHeaders header, Object param, Object body, Class<?> collectionClass,
               Class<?>... elementClasses);

    /**
     * save file from url
     *
     * @param destUrl
     * @param file
     * @throws IOException
     */
    void saveFile(String destUrl, File file) throws IOException;

}
