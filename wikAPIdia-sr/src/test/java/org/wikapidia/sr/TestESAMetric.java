package org.wikapidia.sr;

import com.jolbox.bonecp.BoneCPDataSource;
import org.apache.lucene.util.Version;
import org.junit.Test;
import org.wikapidia.conf.Configuration;
import org.wikapidia.conf.ConfigurationException;
import org.wikapidia.conf.Configurator;
import org.wikapidia.core.WikapidiaException;
import org.wikapidia.core.dao.DaoException;
import org.wikapidia.core.dao.LocalPageDao;
import org.wikapidia.core.dao.sql.LocalArticleSqlDao;
import org.wikapidia.core.dao.sql.LocalLinkSqlDao;
import org.wikapidia.core.lang.Language;
import org.wikapidia.core.lang.LanguageInfo;
import org.wikapidia.core.lang.LanguageSet;
import org.wikapidia.core.model.LocalPage;
import org.wikapidia.core.model.NameSpace;
import org.wikapidia.core.model.Title;
import org.wikapidia.lucene.LuceneOptions;
import org.wikapidia.lucene.LuceneSearcher;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 *
 */
public class TestESAMetric {

    private static void printResult(SRResult result){
        if (result == null){
            System.out.println("Result was null");
        }
        else {
            System.out.println("Similarity value: "+result.getValue());
            int explanationsSeen = 0;
            for (Explanation explanation : result.getExplanations()){
                System.out.println(explanation.getPlaintext());
                if (++explanationsSeen>5){
                    break;
                }
            }
        }

    }

    private static void printResult(SRResultList results){
        if (results == null){
            System.out.println("Result was null");
        }
        else {
            for (SRResult srResult : results) {
                printResult(srResult);
            }
        }
    }

//    @Test
//    public void testLuceneOptions() {
//        LuceneOptions options = LuceneOptions.getDefaultOptions();
//        assert (options.matchVersion == Version.LUCENE_43);
//    }




    @Test
    public void testMostSimilarPages() throws WikapidiaException, DaoException, ConfigurationException, ClassNotFoundException, IOException {

        Configurator c = new Configurator(new Configuration());
        LocalPageDao localPageDao = c.get(LocalPageDao.class);

        Language lang = Language.getByLangCode("simple");
        LuceneSearcher searcher = new LuceneSearcher(new LanguageSet(Arrays.asList(lang)), LuceneOptions.getDefaultOptions());

        ESAMetric esaMetric = new ESAMetric(lang, searcher, localPageDao);

        String string1 = "Physics";
        String string2 = "Canada";

        LocalPage page1 = localPageDao.getByTitle(lang, new Title(string1, lang), NameSpace.ARTICLE);
        LocalPage page2 = localPageDao.getByTitle(lang, new Title(string2, lang), NameSpace.ARTICLE);

        System.out.println(page1);
        SRResultList srResults= esaMetric.mostSimilar(page1, 20, true);
        for (SRResult srResult : srResults) {
            printResult(srResult);
        }
        System.out.println(Arrays.toString(srResults.getScoresAsFloat()));

        System.out.println(page2);
        SRResultList srResults2= esaMetric.mostSimilar(page2, 20, true);
        for (SRResult srResult : srResults2) {
            printResult(srResult);
        }
        System.out.println(Arrays.toString(srResults2.getScoresAsFloat()));
    }

//    @Test
//    public void testPhraseSimilarity() throws DaoException {
//        Language testLanguage = Language.getByLangCode("simple");
//        LuceneSearcher searcher = new LuceneSearcher(new LanguageSet(Arrays.asList(testLanguage)), LuceneOptions.getDefaultOptions());
//        ESAMetric esaMetric = new ESAMetric(testLanguage, searcher);
//        String[] testPhrases = {"United States", "Barack Obama", "geometry", "machine learning"};
//        for (int i = 0; i < testPhrases.length; i++) {
//            for (int j = i; j < testPhrases.length; j++) {
//                SRResult srResult = esaMetric.similarity(testPhrases[i], testPhrases[j], testLanguage, false);
//                System.out.println("Similarity score between " + testPhrases[i] + " and " + testPhrases[j] + " is " + srResult.getValue());
//            }
//        }
//    }

}
