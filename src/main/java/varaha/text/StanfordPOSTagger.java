/*
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

package varaha.text;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import org.apache.pig.EvalFunc;
import org.apache.pig.data.BagFactory;
import org.apache.pig.data.DataBag;
import org.apache.pig.data.Tuple;
import org.apache.pig.data.TupleFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * StanfordPOSTagger uses the Stanford Maximum Entropy Tagger class to Part-Of-Speech tag a
 * raw text input. Output is a pig bag containing two-field tuples, of the format (word, tag).
 * <p/>
 * <dt><b>Example:</b></dt>
 * <dd><code>
 * register varaha.jar;<br/>
 * documents    = LOAD 'documents' AS (doc_id:chararray, text:chararray);<br/>
 * tokenized    = FOREACH documents GENERATE doc_id AS doc_id, StanfordPOSTagger(text)
 * AS (b:bag{token:tuple(word:chararray, tag:chararray)});
 * </code></dd>
 * </dl>
 *
 * @author Russell Jurney
 * @see
 */
public class StanfordPOSTagger extends EvalFunc<DataBag> {

    private static TupleFactory tupleFactory = TupleFactory.getInstance();
    private static BagFactory bagFactory = BagFactory.getInstance();
    private static boolean isFirst = true;
    private static MaxentTagger tagger;

    private String _model = "src/resources/test/english-left3words-distsim.tagger";


    public StanfordPOSTagger() {

    }

    public StanfordPOSTagger(String model) {

        _model = model;

    }

    // Must also add implementation for bag sof tuples of sentences
    public DataBag exec(Tuple input) throws IOException {
        if (input == null || input.size() < 1 || input.isNull(0))
            return null;

        if (isFirst) {
            try {
                tagger = new MaxentTagger(_model);
            } catch (Exception e) {
                System.err.println("Exception loading language model: " + e.getMessage());
            }
            isFirst = false;
        }

        // Output bag
        DataBag bagOfTokens = bagFactory.newDefaultBag();

        Object inThing = input.get(0);
        if (inThing instanceof String) {
            StringReader textInput = new StringReader((String) inThing);

            // Convert StringReader to String via StringBuilder
            //using string builder is more efficient than concating strings together.
            StringBuilder builder = new StringBuilder();
            int charsRead = -1;
            char[] chars = new char[100];
            do {
                charsRead = textInput.read(chars, 0, chars.length);
                //if we have valid chars, append them to end of string.
                if (charsRead > 0) {
                    builder.append(chars, 0, charsRead);
                }
            }
            while (charsRead > 0);


            // Tagging with the Stanford tagger produces another string, format: word_TAG
            String stringReadFromReader = builder.toString();
            String tagged = tagger.tagString(stringReadFromReader);
            StringReader taggedInput = new StringReader(tagged);


            //won't use tokenizer, as it splits also on ._., instead use plain white space regex
            //PTBTokenizer ptbt = new PTBTokenizer(taggedInput , new CoreLabelTokenFactory(),  "invertible=true,untokenizable=allKeep");

            // Now split based on '_' and build/return a bag of 2-field tuples
            Tuple termText = tupleFactory.newTuple();
            String[] tokens = tagged.split("\\s+");
            //for (CoreLabel label; ptbt.hasNext(); ) {
            for (String s : tokens) {
                //label = (CoreLabel)ptbt.next();
                String word = s; //label.word();
                String[] parts = word.split("_");
                List<String> token = Arrays.asList(parts);

                termText = tupleFactory.newTuple(token);
                bagOfTokens.add(termText);
            }
            //bagOfTokens.add(termText);
        } else if (inThing instanceof DataBag) {
            Iterator<Tuple> itr = ((DataBag) inThing).iterator();
            List<Word> sentence = null;
            while (itr.hasNext()) {
                Tuple t = itr.next();
                if (t.get(0) != null) {
                    Word word = new Word(t.get(0).toString());
                    sentence.add(word);
                }
            }
            ArrayList<TaggedWord> tagged_sentence = tagger.apply(sentence);
            for (TaggedWord tw : tagged_sentence) {
                ArrayList values = new ArrayList();
                values.add(tw.word());
                values.add(tw.toString("_"));
                Tuple t = tupleFactory.newTuple(values);
                bagOfTokens.add(t);
            }
        } else {
            throw new IOException();
        }
        return bagOfTokens;
    }
}
