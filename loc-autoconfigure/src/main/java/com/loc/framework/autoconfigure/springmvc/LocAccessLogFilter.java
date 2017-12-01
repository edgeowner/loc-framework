package com.loc.framework.autoconfigure.springmvc;

import org.springframework.util.StopWatch;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created on 2017/11/30.
 */

@Slf4j
@AllArgsConstructor
public class LocAccessLogFilter extends OncePerRequestFilter {

  private LocSpringMvcProperties properties;

  private final static String DEFAULT_SKIP_PATTERN = "/api-docs.*|/autoconfig|/env|/configprops|/dump|/health|/info|/metrics.*|/mappings|/trace|/swagger.*|.*\\.png|.*\\.css|.*\\.js|.*\\.html|/favicon.ico|/hystrix.stream";

  private final static Pattern SKIP_PATTERNS = Pattern.compile(DEFAULT_SKIP_PATTERN);

  @Override
  protected void doFilterInternal(HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse, FilterChain filterChain)
      throws ServletException, IOException {
    if (noContain(httpServletRequest)) {
      filterChain.doFilter(httpServletRequest, httpServletResponse);
    } else {
      final boolean isFirstRequest = !isAsyncDispatch(httpServletRequest);
      final LocAccessLogger accessLogger = new LocAccessLogger(this.properties);
      HttpServletRequest requestToUse = httpServletRequest;
      ContentCachingResponseWrapper responseToUse = null;

      StopWatch watch = new StopWatch();
      watch.start();
      if (properties
          .isIncludeRequest() && isFirstRequest && !(httpServletRequest instanceof ContentCachingRequestWrapper)) {
        requestToUse = new ContentCachingRequestWrapper(httpServletRequest,
            properties.getRequestBodyLength());
        responseToUse = new ContentCachingResponseWrapper(httpServletResponse);
      }

      if (properties.isIncludeRequest() && isFirstRequest) {
        accessLogger.appendRequestMessage(requestToUse);
      }
      try {
        filterChain.doFilter(requestToUse, responseToUse);
      } finally {
        if (properties.isIncludeResponse() && !isAsyncStarted(requestToUse) && !isBinaryContent(
            httpServletResponse) && !isMultipart(httpServletResponse)) {
          accessLogger.appendResponseMessage(responseToUse);
        }
        accessLogger.appendTime(watch.getTotalTimeMillis());
        accessLogger.printLog();
      }
    }
  }

  private boolean noContain(HttpServletRequest request) {
    String path = request.getServletPath();
    return !SKIP_PATTERNS.matcher(path).matches();
  }

  private boolean isBinaryContent(final HttpServletResponse response) {
    return response.getContentType() != null && (response.getContentType()
        .startsWith("image") || response.getContentType().startsWith("video") || response
        .getContentType().startsWith("audio"));
  }

  private boolean isMultipart(final HttpServletResponse response) {
    return response.getContentType() != null && (response.getContentType()
        .startsWith("multipart/form-data") || response.getContentType()
        .startsWith("application/octet-stream"));
  }
}
