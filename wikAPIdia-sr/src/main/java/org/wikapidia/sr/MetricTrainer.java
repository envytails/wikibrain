package org.wikapidia.sr;

import org.apache.commons.cli.*;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.PosixParser;
import org.wikapidia.conf.Configuration;
import org.wikapidia.conf.ConfigurationException;
import org.wikapidia.conf.Configurator;
import org.wikapidia.conf.DefaultOptionBuilder;
import org.wikapidia.core.cmd.Env;
import org.wikapidia.core.dao.DaoException;
import org.wikapidia.core.lang.Language;
import org.wikapidia.sr.normalize.Normalizer;
import org.wikapidia.sr.utils.Dataset;
import org.wikapidia.sr.utils.DatasetDao;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matt Lesciko
 * @author Ben Hillmann
 */
public class MetricTrainer {

    public static void main(String[] args) throws ConfigurationException, DaoException, IOException, ClassNotFoundException {
        Options options = new Options();

//        //Which normalizer to use
//        options.addOption(
//                new DefaultOptionBuilder()
//                        .withLongOpt("normalizer")
//                        .withDescription("specify which normalizer to use")
//                        .create("n"));

        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("universal")
                        .withDescription("set a universal metric")
                        .create("u"));
        //Number of Max Results(otherwise take from config)
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("max-results")
                        .withDescription("the set of algorithms for universal pages to process, separated by commas")
                        .create("r"));
        //Specify the Dataset
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArgs()
                        .withLongOpt("gold")
                        .withDescription("the set of gold standard datasets to train on, separated by commas")
                        .create("g"));
        //Specify the Metrics
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("metrics")
                        .withDescription("set a local metric")
                        .create("m"));

        Env.addStandardOptions(options);


        CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("MetricTrainer", options);
            return;
        }

        Env env = new Env(cmd);
        Configurator c = env.getConfigurator();

        LocalSRMetric sr=null;
        UniversalSRMetric usr=null;
        Normalizer localNormalizer=null;
        Normalizer universalNormalizer=null;
        if (cmd.hasOption("m")){
            sr = c.get(LocalSRMetric.class,cmd.getOptionValue("m"));
            String normalizer = c.getConf().get().getString("sr.metric.local."+cmd.getOptionValue("m")+".normalizer");
            localNormalizer = c.get(Normalizer.class,normalizer);
        }
        if (cmd.hasOption("u")){
            usr = c.get(UniversalSRMetric.class,cmd.getOptionValue("u"));
            String normalizer = c.getConf().get().getString("sr.metric.universal."+cmd.getOptionValue("u")+".normalizer");
            universalNormalizer = c.get(Normalizer.class,normalizer);
        }

        if (sr==null&&usr==null){
            throw new IllegalArgumentException("Must specify a metric to train.");
        }

        List<String> datasetConfig = c.getConf().get().getStringList("sr.dataset.names");

        int maxResults = cmd.hasOption("r")? Integer.parseInt(cmd.getOptionValue("r")) : c.getConf().get().getInt("sr.normalizer.defaultmaxresults");



        if (datasetConfig.size()%2 != 0) {
            throw new ConfigurationException("Datasets must be paired with a matching language");
        }

        String datasetPath = c.getConf().get().getString("sr.dataset.path");
        String normalizerPath = c.getConf().get().getString("sr.normalizer.directory");

        List<Dataset> datasets = new ArrayList<Dataset>();
        DatasetDao datasetDao = new DatasetDao();

        if (cmd.hasOption("g")){
            String[] datasetNames = cmd.getOptionValues("g");
            for (String name : datasetNames){
                if (datasetConfig.contains(name)){
                    int langPosition = datasetConfig.indexOf(name)-1;
                    Language language = Language.getByLangCode(datasetConfig.get(langPosition));
                    datasets.add(datasetDao.read(language,datasetPath+name));
                }
                else {
                    throw new IllegalArgumentException("Specified dataset "+name+" is not in the configuration file.");
                }
            }
        }
        else{
            for (int i = 0; i < datasetConfig.size();i+=2) {
                String language = datasetConfig.get(i);
                String datasetName = datasetConfig.get(i+1);
                datasets.add(datasetDao.read(Language.getByLangCode(language), datasetPath + datasetName));
            }
        }

        for (Dataset dataset: datasets) {
            if (usr!=null){
                usr.setSimilarityNormalizer(universalNormalizer);
                usr.trainSimilarity(dataset);
                usr.setMostSimilarNormalizer(universalNormalizer);
                usr.trainMostSimilar(dataset,maxResults,null);
            }
            if (sr!=null){
                sr.setDefaultSimilarityNormalizer(localNormalizer);
                sr.trainDefaultSimilarity(dataset);
                sr.setDefaultMostSimilarNormalizer(localNormalizer);
                sr.trainDefaultMostSimilar(dataset,maxResults,null);
                sr.setSimilarityNormalizer(localNormalizer,dataset.getLanguage());
                sr.trainSimilarity(dataset);
                sr.setMostSimilarNormalizer(localNormalizer,dataset.getLanguage());
                sr.trainMostSimilar(dataset,maxResults,null);
            }
        }

        if (usr!=null){usr.write(normalizerPath);usr.read(normalizerPath);}
        if (sr!=null){sr.write(normalizerPath);sr.read(normalizerPath);}


        System.out.println(datasets.get(0));



    }
}
