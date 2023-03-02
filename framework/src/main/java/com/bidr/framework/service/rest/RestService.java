/**
 * @Title: RestService.java Copyright (c) 2019 Sharp. All rights reserved.
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.service
 * @author sharp
 * @date 2019-01-23 09:37:10
 */
package com.bidr.framework.service.rest;

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
     * @param <T>   the type parameter
     * @param url   the url
     * @param clazz the clazz
     * @return the t
     */
    <T> T get(String url, Class<T> clazz);

    /**
     * Get t.
     *
     * @param <T>    the type parameter
     * @param url    the url
     * @param header the header
     * @param clazz  the clazz
     * @return the t
     */
    <T> T get(String url, HttpHeaders header, Class<T> clazz);

    /**
     * Get t.
     *
     * @param <T>   the type parameter
     * @param url   the url
     * @param param the param
     * @param clazz the clazz
     * @return the t
     */
    <T> T get(String url, Object param, Class<T> clazz);

    /**
     * Get t.
     *
     * @param <T>    the type parameter
     * @param url    the url
     * @param header the header
     * @param param  the param
     * @param clazz  the clazz
     * @return the t
     */
    <T> T get(String url, HttpHeaders header, Object param, Class<T> clazz);

    /**
     * Post t.
     *
     * @param <T>   the type parameter
     * @param url   the url
     * @param clazz the clazz
     * @param body  the body
     * @return the t
     */
    <T> T post(String url, Class<T> clazz, Object body);

    /**
     * Post t.
     *
     * @param <T>   the type parameter
     * @param url   the url
     * @param clazz the clazz
     * @param body  the body
     * @return the t
     */
    <T> T post(String url, Class<T> clazz, LinkedMultiValueMap body);

    /**
     * Post t.
     *
     * @param <T>    the type parameter
     * @param url    the url
     * @param header the header
     * @param clazz  the clazz
     * @param body   the body
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, Class<T> clazz, Object body);

    /**
     * Post t.
     *
     * @param <T>    the type parameter
     * @param url    the url
     * @param header the header
     * @param clazz  the clazz
     * @param body   the body
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, Class<T> clazz, LinkedMultiValueMap body);

    /**
     * Post t.
     *
     * @param <T>   the type parameter
     * @param url   the url
     * @param clazz the clazz
     * @param param the param
     * @param body  the body
     * @return the t
     */
    <T> T post(String url, Class<T> clazz, Object param, Object body);

    /**
     * Post t.
     *
     * @param <T>   the type parameter
     * @param url   the url
     * @param clazz the clazz
     * @param param the param
     * @param body  the body
     * @return the t
     */
    <T> T post(String url, Class<T> clazz, Object param, LinkedMultiValueMap body);

    /**
     * Post t.
     *
     * @param <T>    the type parameter
     * @param url    the url
     * @param header the header
     * @param clazz  the clazz
     * @param param  the param
     * @param body   the body
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, Class<T> clazz, Object param, Object body);

    /**
     * Post t.
     *
     * @param <T>    the type parameter
     * @param url    the url
     * @param header the header
     * @param clazz  the clazz
     * @param param  the param
     * @param body   the body
     * @return the t
     */
    <T> T post(String url, HttpHeaders header, Class<T> clazz, Object param, LinkedMultiValueMap body);

    /**
     * Exec t.
     *
     * @param <T>    the type parameter
     * @param url    the url
     * @param method the method
     * @param header the header
     * @param clazz  the clazz
     * @param param  the param
     * @param body   the body
     * @return the t
     */
    <T> T exec(String url, HttpMethod method, HttpHeaders header, Class<T> clazz, Object param, Object body);

    /**
     * save file from url
     *
     * @param destUrl
     * @param file
     * @throws IOException
     */
    void saveFile(String destUrl, File file) throws IOException;

}
