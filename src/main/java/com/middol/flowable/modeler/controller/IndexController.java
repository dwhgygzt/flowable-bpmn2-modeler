package com.middol.flowable.modeler.controller;

import com.middol.flowable.modeler.service.PersistentTokenService;
import org.flowable.idm.api.Token;
import org.flowable.ui.common.model.RemoteUser;
import org.flowable.ui.common.model.UserRepresentation;
import org.flowable.ui.common.security.CookieConstants;
import org.flowable.ui.common.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;

/**
 * @author admin
 */
@Controller
public class IndexController {

    @Resource
    private RemoteAccountResourceController resourceController;

    @Resource
    private PersistentTokenService persistentTokenService;

    private static Logger logger = LoggerFactory.getLogger(IndexController.class);

    private void init(HttpServletRequest request, HttpServletResponse response) {
        UserRepresentation user = resourceController.getAccount();
        RemoteUser remoteUser = new RemoteUser();
        remoteUser.setId(user.getId());
        remoteUser.setFirstName(user.getFirstName());
        remoteUser.setLastName(user.getLastName());
        remoteUser.setFullName(user.getFullName());
        remoteUser.setDisplayName(user.getLastName());
        remoteUser.setEmail(user.getEmail());
        remoteUser.setPassword("test");
        remoteUser.setTenantId(user.getTenantId());
        remoteUser.setGroups(new ArrayList<>(2));
        remoteUser.setPrivileges(user.getPrivileges());
        Token token = persistentTokenService.createToken(remoteUser, request.getRemoteAddr(), request.getHeader("User-Agent"));
        addCookie(token, request, response);
        SecurityUtils.assumeUser(remoteUser);
    }

    @RequestMapping(value = "/modeler/index", method = RequestMethod.GET)
    public String modeler(HttpServletRequest request, HttpServletResponse response) {
        init(request, response);
        return "index.html";
    }

    @RequestMapping(value = "/modeler/redirect", method = RequestMethod.GET)
    public void redirect(HttpServletRequest request, HttpServletResponse response, String redirectUrl) throws IOException {
        init(request, response);
        logger.debug("跳转URL {}", URLDecoder.decode(redirectUrl, "UTF-8"));
        response.sendRedirect(URLDecoder.decode(redirectUrl, "UTF-8"));
    }

    @RequestMapping(value = "/modeler/app/logout", method = RequestMethod.GET)
    public String logout(HttpServletRequest req, HttpServletResponse res) {

        String rememberMeCookie = extractRememberMeCookie(req);
        if (rememberMeCookie != null && rememberMeCookie.length() != 0) {
            try {
                String[] cookieTokens = decodeCookie(rememberMeCookie);
                Token token = getPersistentToken(cookieTokens);
                persistentTokenService.delete(token);
            } catch (InvalidCookieException ice) {
                logger.info("Invalid cookie, no persistent token could be deleted");
            } catch (RememberMeAuthenticationException rmae) {
                logger.debug("No persistent token found, so no token could be deleted");
            }
        }

        Cookie cookie = new Cookie(CookieConstants.COOKIE_NAME, null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        res.addCookie(cookie);

        return "/modeler/index";
    }

    protected void addCookie(Token token, HttpServletRequest request, HttpServletResponse response) {
        setCookie(new String[]{token.getId(), token.getTokenValue()}, request, response);
    }

    protected void setCookie(String[] tokens, HttpServletRequest request, HttpServletResponse response) {
        String https = "https";
        String cookieValue = encodeCookie(tokens);
        Cookie cookie = new Cookie(CookieConstants.COOKIE_NAME, cookieValue);
        cookie.setMaxAge(2678400);
        cookie.setPath("/");

        String xForwardedProtoHeader = request.getHeader("X-Forwarded-Proto");
        if (xForwardedProtoHeader != null) {
            cookie.setSecure(xForwardedProtoHeader.equals(https) || request.isSecure());
        } else {
            cookie.setSecure(request.isSecure());
        }

        Method setHttpOnlyMethod = ReflectionUtils.findMethod(Cookie.class, "setHttpOnly", boolean.class);
        if (setHttpOnlyMethod != null) {
            ReflectionUtils.invokeMethod(setHttpOnlyMethod, cookie, Boolean.TRUE);
        } else if (logger.isDebugEnabled()) {
            logger.debug("Note: Cookie will not be marked as HttpOnly because you are not using Servlet 3.0 (Cookie#setHttpOnly(boolean) was not found).");
        }

        response.addCookie(cookie);
    }

    /**
     * Inverse operation of decodeCookie.
     *
     * @param cookieTokens the tokens to be encoded.
     * @return base64 encoding of the tokens concatenated with the ":" delimiter.
     */
    protected String encodeCookie(String[] cookieTokens) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cookieTokens.length; i++) {
            try {
                sb.append(URLEncoder.encode(cookieTokens[i], StandardCharsets.UTF_8.toString()));
            } catch (UnsupportedEncodingException e) {
                logger.error(e.getMessage(), e);
            }

            if (i < cookieTokens.length - 1) {
                sb.append(":");
            }
        }

        String value = sb.toString();

        sb = new StringBuilder(new String(Base64.getEncoder().encode(value.getBytes())));
        char dh = '=';
        while (sb.charAt(sb.length() - 1) == dh) {
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * Decodes the cookie and splits it into a set of token strings using the ":"
     * delimiter.
     *
     * @param cookieValue the value obtained from the submitted cookie
     * @return the array of tokens.
     * @throws InvalidCookieException if the cookie was not base64 encoded.
     */
    protected String[] decodeCookie(String cookieValue) throws InvalidCookieException {
        StringBuilder cookieValueBuilder = new StringBuilder(cookieValue);
        int cnt = 4;
        for (int j = 0; j < cookieValueBuilder.length() % cnt; j++) {
            cookieValueBuilder.append("=");
        }
        cookieValue = cookieValueBuilder.toString();

        try {
            Base64.getDecoder().decode(cookieValue.getBytes());
        } catch (IllegalArgumentException e) {
            throw new InvalidCookieException(
                    "Cookie token was not Base64 encoded; value was '" + cookieValue
                            + "'");
        }

        String cookieAsPlainText = new String(Base64.getDecoder().decode(cookieValue.getBytes()));

        String[] tokens = StringUtils.delimitedListToStringArray(cookieAsPlainText,
                ":");

        for (int i = 0; i < tokens.length; i++) {
            try {
                tokens[i] = URLDecoder.decode(tokens[i], StandardCharsets.UTF_8.toString());
            } catch (UnsupportedEncodingException e) {
                logger.error(e.getMessage(), e);
            }
        }

        return tokens;
    }

    /**
     * Locates the Spring Security remember me cookie in the request and returns its
     * value. The cookie is searched for by name and also by matching the context path to
     * the cookie path.
     *
     * @param request the submitted request which is to be authenticated
     * @return the cookie value (if present), null otherwise.
     */
    protected String extractRememberMeCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if ((cookies == null) || (cookies.length == 0)) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (CookieConstants.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    /**
     * Validate the token and return it.
     */
    protected Token getPersistentToken(String[] cookieTokens) {
        int cnt = 2;
        if (cookieTokens.length != cnt) {
            throw new InvalidCookieException("Cookie token did not contain " + cnt + " tokens, but contained '" + Arrays.asList(cookieTokens) + "'");
        }

        final String presentedSeries = cookieTokens[0];
        final String presentedToken = cookieTokens[1];

        Token token = persistentTokenService.getPersistentToken(presentedSeries);

        if (token == null) {
            // No series match, so we can't authenticate using this cookie
            throw new RememberMeAuthenticationException("No persistent token found for series id: " + presentedSeries);
        }

        // We have a match for this user/series combination
        if (!presentedToken.equals(token.getTokenValue())) {

            // This could be caused by the opportunity window where the token just has been refreshed, but
            // has not been put into the token cache yet. Invalidate the token and refetch and it the new token value from the db is now returned.
            // Note the 'true' here, which invalidates the cache before fetching
            token = persistentTokenService.getPersistentToken(presentedSeries, true);
            if (token != null && !presentedToken.equals(token.getTokenValue())) {

                // Token doesn't match series value. Delete this session and throw an exception.
                persistentTokenService.delete(token);

                throw new CookieTheftException("Invalid remember-me token (Series/token) mismatch. Implies previous cookie theft attack.");

            }
        }
        long maxAge = 2678400 * 1000L;
        if (token != null && System.currentTimeMillis() - token.getTokenDate().getTime() > maxAge) {
            throw new RememberMeAuthenticationException("Remember-me login has expired");
        }
        return token;
    }
}
