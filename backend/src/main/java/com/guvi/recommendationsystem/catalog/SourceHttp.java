package com.guvi.recommendationsystem.catalog;

import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.util.Timeout;

/** Only reviewed public sources, never a general URL proxy. Redirects receive the same checks. */
public final class SourceHttp {
    private static final Set<String> HOSTS = Set.of("api.learn.mit.edu", "ocw.mit.edu", "medlineplus.gov", "archive.ics.uci.edu",
            "openstax.org", "learn.microsoft.com", "raw.githubusercontent.com");
    private long lastRequest;

    public static URI safeUri(String value) {
        try {
            URI uri = URI.create(value).normalize();
            if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()) || uri.getUserInfo()!=null
                    || (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getFragment()!=null)
                throw new IllegalArgumentException("Only reviewed HTTPS source hosts are allowed.");
            return uri;
        } catch (RuntimeException ex) { throw new IllegalArgumentException("Unsafe source URL.",ex); }
    }

    static void publicAddress(InetAddress address) {
        byte[] b=address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()
                || (b.length==16 && unsafeIpv6(b))
                || (b.length==4 && ((b[0]&255)==0 || (b[0]&255)==127 || (b[0]&255)>=224
                || ((b[0]&255)==192 && (b[1]&255)==0)
                || ((b[0]&255)==198 && ((b[1]&255)==18 || (b[1]&255)==19 || (b[1]&255)==51 && (b[2]&255)==100))
                || ((b[0]&255)==203 && (b[1]&255)==0 && (b[2]&255)==113)
                || ((b[0]&255)==100 && (b[1]&255)>=64 && (b[1]&255)<=127)
                || ((b[0]&255)==169 && (b[1]&255)==254))))
            throw new IllegalArgumentException("Private or reserved source destination rejected.");
    }

    /** Fixed publisher hosts must resolve to ordinary global unicast, not protocol/tunnel or documentation space.
     * IANA special-purpose registry: 2001::/23, 2001:db8::/32, 2002::/16 and 3fff::/20.
     * Rejecting transition networks also avoids treating embedded private IPv4 addresses as public destinations.
     */
    private static boolean unsafeIpv6(byte[] address) {
        int first=address[0]&255, second=address[1]&255, third=address[2]&255, fourth=address[3]&255;
        return (first&0xe0)!=0x20
                || first==0x20 && second==1 && (third&0xfe)==0
                || first==0x20 && second==1 && third==0x0d && fourth==0xb8
                || first==0x20 && second==2
                || first==0x3f && second==0xff && (third&0xf0)==0;
    }

    public record Result(int status, URI destination, byte[] body) {
        public Result { body = body.clone(); }
        @Override public byte[] body() { return body.clone(); }
    }

    public synchronized Result request(String url, boolean head, int maxBytes) throws Exception {
        if(maxBytes<0 || maxBytes>60_000_000)throw new IllegalArgumentException("Invalid source response limit.");
        URI uri=safeUri(url);
        // The resolver used by the actual connection validates every returned address.
        // TLS still verifies the original hostname; no proxy, automatic redirects or retries.
        var connections=PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new SystemDefaultDnsResolver(){
                    @Override public InetAddress[] resolve(String host) throws UnknownHostException {
                        var addresses=super.resolve(host);
                        for(var address:addresses)publicAddress(address);
                        return addresses;
                    }
                })
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(10))
                        .setSocketTimeout(Timeout.ofSeconds(30)).build()).build();
        try(var client=HttpClients.custom().setConnectionManager(connections)
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement()
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(30))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(10)).build()).build()) {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        for(int redirects=0;redirects<=4;redirects++) {
            if(System.nanoTime()>deadline)throw new java.net.SocketTimeoutException("Source request deadline exceeded.");
            long pause=1000-(System.currentTimeMillis()-lastRequest);
            if(pause>0)Thread.sleep(pause);
            lastRequest=System.currentTimeMillis();
            var request=new HttpUriRequestBase(head?"HEAD":"GET",uri);
            request.setHeader("User-Agent","RecomAI-University-Research/4.0 (bounded educational metadata acquisition)");
            try(var response=client.executeOpen(null,request,null)) {
                if(Set.of(301,302,303,307,308).contains(response.getCode())) {
                    var location=response.getFirstHeader("Location");
                    if(location==null)throw new IOException("Source redirect has no destination.");
                    uri=safeUri(uri.resolve(location.getValue()).toString());continue;
                }
                if(head || response.getEntity()==null)return new Result(response.getCode(),uri,new byte[0]);
                if(response.getEntity().getContentLength()>maxBytes)throw new IOException("Source response exceeds size limit.");
                try(var stream=response.getEntity().getContent();var bytes=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192];int count;
                    while((count=stream.read(buffer))!=-1){
                        if(System.nanoTime()>deadline)throw new java.net.SocketTimeoutException("Source request deadline exceeded.");
                        if(bytes.size()+count>maxBytes)throw new IOException("Source response exceeds size limit.");
                        bytes.write(buffer,0,count);
                    }
                    return new Result(response.getCode(),uri,bytes.toByteArray());
                }
            }
        }
        throw new IOException("Source redirect limit exceeded.");
        }
    }
}
