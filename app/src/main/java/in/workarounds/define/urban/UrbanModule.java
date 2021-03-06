package in.workarounds.define.urban;

import com.squareup.okhttp.OkHttpClient;

import dagger.Module;
import dagger.Provides;
import in.workarounds.define.portal.PerPortal;
import retrofit.GsonConverterFactory;
import retrofit.Retrofit;

/**
 * Created by madki on 26/09/15.
 */
@Module
public class UrbanModule {

    @Provides @PerPortal
    public UrbanApi provideUrbanApi(OkHttpClient client) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://api.urbandictionary.com/v0/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(UrbanApi.class);
    }
}
