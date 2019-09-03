/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.index.query;

import org.apache.logging.log4j.LogManager;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.index.*;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PositionMatchScorer extends Scorer {
    private final int NOT_FOUND_POSITION = -1;
    private final int HALF_SCORE_POSITION = 5; // position where score should decrease by 50%

    private final LeafReaderContext context;
    private final Scorer scorer;
    private float boost;

    PositionMatchScorer(PositionMatchWeight weight, LeafReaderContext context) throws IOException {
        super(weight);
        this.context = context;
        this.scorer = weight.weight.scorer(context);
        this.boost  = weight.boost();
    }

    @Override
    public DocIdSetIterator iterator() {
        if (scorer != null) {
            return scorer.iterator();
        } else {
            return DocIdSetIterator.empty();
        }
    }

    @Override
    public float getMaxScore(int upTo) throws IOException {
        if (scorer != null) {
            return scorer.getMaxScore(upTo);
        } else {
            return 0.0f;
        }
    }

    @Override
    public float score() {
        int doc = docID();
        float totalScore = 0.0f;

        Set<Term> terms = new HashSet<>();
        weight.extractTerms(terms);

        for (Term term : terms) {
            totalScore += scoreTerm(doc, term);
        }
        
        if (this.boost > 0) {
            totalScore = totalScore * this.boost;
        }

        return totalScore;
    }

    Explanation explain(int docID) {
        List<Explanation> explanations = new ArrayList<>();

        float totalScore = 0.0f;

        Set<Term> terms = new HashSet<>();
        weight.extractTerms(terms);

        for (Term term : terms) {
            Explanation termExplanation = explainTerm(docID, term);
            explanations.add(termExplanation);
            totalScore += termExplanation.getValue().floatValue();
        }

        return Explanation.match(
            totalScore,
            String.format("score(doc=%d), sum of:", docID),
            explanations
        );
    }

    @Override
    public int docID() {
        if (scorer != null) {
            return scorer.docID();
        } else {
            return NOT_FOUND_POSITION;
        }
    }

    private float scoreTerm(int docID, Term term) {
        float termScore = 0.0f; // default score
        int termPosition = position(docID, term);
        if (NOT_FOUND_POSITION != termPosition) termScore = ((float) HALF_SCORE_POSITION) / (HALF_SCORE_POSITION + termPosition);
        return termScore;
    }

    private Explanation explainTerm(int docID, Term term) {
        int termPosition = position(docID, term);
        if (NOT_FOUND_POSITION == termPosition) {
            return Explanation.noMatch(
                String.format("no matching terms for field=%s, term=%s", term.field(), term.text())
            );
        } else {
            float termScore = ((float) HALF_SCORE_POSITION) / (HALF_SCORE_POSITION + termPosition);
            String func = HALF_SCORE_POSITION + "/(" + HALF_SCORE_POSITION + "+" + termPosition + ")";
            return Explanation.match(
                termScore,
                String.format("score(field=%s, term=%s, pos=%d, func=%s, boost=%f)", term.field(), term.text(), termPosition, func, this.boost)
            );
        }
    }

    private int position(int docID, Term term) {
        try {
            Terms terms = context.reader().getTermVector(docID, term.field());
            if (terms == null) {
                return NOT_FOUND_POSITION;
            }
            TermsEnum termsEnum = terms.iterator();
            if (!termsEnum.seekExact(term.bytes())) {
                return NOT_FOUND_POSITION;
            }
            PostingsEnum dpEnum = termsEnum.postings(null, PostingsEnum.ALL);

            if (dpEnum == null) {
                return NOT_FOUND_POSITION;
            }

            //return NOT_FOUND_POSITION;

            dpEnum.nextDoc();
            return dpEnum.nextPosition();
        } catch (UnsupportedOperationException ex) {
            LogManager.getLogger(this.getClass()).error("Unsupported operation, returning position = " +
                NOT_FOUND_POSITION + " for field = " + term.field());
            return NOT_FOUND_POSITION;
        } catch (Exception ex) {
            LogManager.getLogger(this.getClass()).error("Unexpected exception, returning position = " +
                NOT_FOUND_POSITION + " for field = " + term.field());
            return NOT_FOUND_POSITION;
        }
    }
}
