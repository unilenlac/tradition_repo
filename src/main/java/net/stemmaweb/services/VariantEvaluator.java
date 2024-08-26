package net.stemmaweb.services;

import net.stemmaweb.rest.ERelations;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;

import java.lang.reflect.Array;
import java.util.*;

class VariantEvaluator implements Evaluator {

    private final Long witness_count;
    Integer last_visited_relation_w;
    Integer last_visited_path_l;

    VariantEvaluator(Long witness_count) {
        this.witness_count = witness_count;
    }

    @Override
    public Evaluation evaluate(Path path) {
        // the idea here is to select a path by following relations where a majority
        // of witnesses are recorded in the 'witnesses' relation attribute. When relations
        // have the same amount of witnesses, we select the first incoming path.
        // Not very straightforward, but it's simple, and it works.
        Relationship last_rel = path.lastRelationship();
        if(last_rel == null){
            return Evaluation.INCLUDE_AND_CONTINUE;
        }else{
            Node start_node = last_rel.getStartNode();
            long variant_size = start_node.getRelationships(Direction.OUTGOING, ERelations.SEQUENCE).stream().count();
            if (variant_size == this.witness_count) {
                return Evaluation.INCLUDE_AND_CONTINUE;
            } else {
                Relationship path_relationship = path.lastRelationship();
                String[] rel_witnesses = (String[]) path_relationship.getProperty("witnesses");
                if((rel_witnesses.length < variant_size && last_visited_relation_w != null && last_visited_relation_w != variant_size) || (rel_witnesses.length < variant_size && last_visited_relation_w == null)) {
                    last_visited_relation_w = rel_witnesses.length;
                    return Evaluation.EXCLUDE_AND_PRUNE;
                } else if(last_visited_relation_w != null && last_visited_relation_w == variant_size) {
                    if(last_visited_path_l != null && last_visited_path_l == path.length()){
                        return Evaluation.EXCLUDE_AND_PRUNE;
                    }else{
                        last_visited_relation_w = rel_witnesses.length;
                        last_visited_path_l = path.length();
                        return Evaluation.INCLUDE_AND_CONTINUE;
                    }
                } else {
                    last_visited_relation_w = rel_witnesses.length;
                    return Evaluation.INCLUDE_AND_CONTINUE;
                }
            }
        }
    }
}