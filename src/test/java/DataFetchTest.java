import com.google.gson.Gson;
import org.assertj.core.api.Assertions;
import org.example.CoinData;
import org.example.CoingeckoApi;
import org.example.DataFecther;
import org.junit.jupiter.api.*;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
@TestMethodOrder(MethodOrderer.Random.class)
public class DataFetchTest {
    DataFetchTest(){

    }
     DataFecther dataFecther;
    @BeforeEach
     void createMocks(){
        System.out.println("before each" + this);
        dataFecther = new DataFecther();
        System.out.println(dataFecther);
    }

    @Tag("exception")

    @Test
    void NullExceptionParseCoin(){

        assertAll(
                ()-> {
                    NullPointerException nullPointerException =  assertThrows(NullPointerException.class, () -> {
                        List<String> a = null;
                        a.get(1);
                        fail();
                    });
                    assertThat(nullPointerException
                    .getMessage()).isEqualTo("Cannot invoke " +
                    "\"java.util.List.get(int)\" because \"a\" is null");
                },
                ()->assertThrows(NullPointerException.class,()->{
                    List<String >a = null;
                    a.add("string");
                    fail()  ;
                })
        );

    }
   //  @Tag("exception")
    @Test
    public void NullDataHTTPTest(){}

    @Test
    void testApiRequestTypeIsEmpty(){

        HttpResponse<String> fetchedData =dataFecther.fetchData("bitcoin");
        if(fetchedData==null){
            fail("data fetched incorrectly api doesnt respond");
        }else {
            assertTrue(true, ()-> "data fetched correctly");
        }
    }
    @AfterEach
    void afterEach(){
        System.out.println("after all" + this);
    }





}
