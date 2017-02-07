/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.migu.flume.interceptor.plugin;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * Interceptor that allows search-and-replace of event body strings using
 * regular expressions. This only works with event bodies that are valid
 * strings. The charset is configurable.
 * <p>
 * Usage:
 * <pre>
 *   agent.source-1.interceptors.search-replace.searchPattern = ^INFO:
 *   agent.source-1.interceptors.search-replace.replaceString = Log msg:
 * </pre>
 * <p>
 * Any regular expression search pattern and replacement pattern that can be
 * used with {@link Matcher#replaceAll(String)} may be used,
 * including backtracking and grouping.
 */
public class SearchAndAppendInterceptor implements Interceptor {

  private static final Logger logger = LoggerFactory
      .getLogger(SearchAndAppendInterceptor.class);

  //修改为String类型，可以添加多个匹配正则表达式
  private final String searchPattern;
  //自定义配置参数，定界符
  private final String appendDelimiterString;
  private final Charset charset;

  private SearchAndAppendInterceptor(String searchPattern,
                                     String appendDelimiterString,
                                     Charset charset) {
    this.searchPattern = searchPattern;
    this.appendDelimiterString = appendDelimiterString;
    this.charset = charset;
  }

  @Override
  public void initialize() {
  }

  @Override
  public void close() {
  }

  @Override
  public Event intercept(Event event) {
    String origBody = new String(event.getBody(), charset);
    // = Pattern.compile(searchPattern);
    //处理多个正则表达式
    String[] searchRegexs = searchPattern.split(",");
    int l = searchRegexs.length;
    StringBuilder appendString = new StringBuilder();
    for(int i=0;i<l;i++){
      Pattern pattern = Pattern.compile(searchRegexs[i]);
      Matcher matcher = pattern.matcher(origBody);
      if(matcher.find()&&matcher.groupCount()>0){
        //提取第一个括号中匹配的内容
        appendString.append(appendDelimiterString+matcher.group(1));
      }else{
        appendString.append(appendDelimiterString+"NULL");
      }
    }

    //Matcher matcher = searchPattern.matcher(origBody);
   // String newBody = matcher.replaceAll(replaceString);
    //新的字符串
    String newBody = origBody+appendString;
    event.setBody(newBody.getBytes(charset));
    return event;
  }

  @Override
  public List<Event> intercept(List<Event> events) {
    for (Event event : events) {
      intercept(event);
    }
    return events;
  }

  public static class Builder implements Interceptor.Builder {
    private static final String SEARCH_PAT_KEY = "searchPattern";
    //追加内容的分隔符默认为"|"
    private static final String APPEND_DELIMITER_KEY = "appendDelimiter";
    private static final String CHARSET_KEY = "charset";

    //修改为String类型，可以添加多个匹配正则表达式
    private String searchRegex;
    private String appendDelimiterString;
    private Charset charset = Charsets.UTF_8;

    @Override
    public void configure(Context context) {
      String searchPattern = context.getString(SEARCH_PAT_KEY);
      Preconditions.checkArgument(!StringUtils.isEmpty(searchPattern),
          "Must supply a valid search pattern " + SEARCH_PAT_KEY +
          " (may not be empty)");

      appendDelimiterString = context.getString(APPEND_DELIMITER_KEY);
      //如果定界符为空赋值默认定界符"|"
      if (appendDelimiterString == null) {
        appendDelimiterString = "|";
      }

      //正则表达式改为在intercept函数中编译，方便处理多个正则表达式串
     // searchRegex = Pattern.compile(searchPattern);
      searchRegex = searchPattern;

      if (context.containsKey(CHARSET_KEY)) {
        // May throw IllegalArgumentException for unsupported charsets.
        charset = Charset.forName(context.getString(CHARSET_KEY));
      }
    }

    @Override
    public Interceptor build() {
      Preconditions.checkNotNull(searchRegex,
                                 "Regular expression search pattern required");
      Preconditions.checkNotNull(appendDelimiterString,
                                 "Replacement string required");
      return new SearchAndAppendInterceptor(searchRegex, appendDelimiterString, charset);
    }
  }
}
