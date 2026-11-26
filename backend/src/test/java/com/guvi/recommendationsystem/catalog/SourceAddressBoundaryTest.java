package com.guvi.recommendationsystem.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetAddress;
import static org.assertj.core.api.Assertions.*;

class SourceAddressBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings={"2002:7f00:1::1", "2002:a00:1::1", "2001:0:4136:e378:8000:63bf:3fff:fdd2",
        "2001:2::1", "2001:10::1", "2001:20::1", "3fff::1", "3fff:fff:ffff::1",
        "64:ff9b::a00:1", "64:ff9b:1::1", "::ffff:127.0.0.1"})
    void rejectsProtocolTranslationAndDocumentationAddresses(String address) throws Exception {
        var resolved=InetAddress.getByName(address);
        assertThatThrownBy(()->SourceHttp.publicAddress(resolved)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings={"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111", "2001:4860:4860::8888"})
    void acceptsOrdinaryGlobalProviderAddresses(String address) throws Exception {
        var resolved=InetAddress.getByName(address);
        assertThatCode(()->SourceHttp.publicAddress(resolved)).doesNotThrowAnyException();
    }
}
