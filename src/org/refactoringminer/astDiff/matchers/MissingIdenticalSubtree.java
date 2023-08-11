package org.refactoringminer.astDiff.matchers;

import com.github.gumtreediff.matchers.*;
import com.github.gumtreediff.matchers.heuristic.gt.DefaultPriorityTreeQueue;
import com.github.gumtreediff.matchers.heuristic.gt.GreedySubtreeMatcher;
import com.github.gumtreediff.matchers.heuristic.gt.MappingComparators;
import com.github.gumtreediff.matchers.heuristic.gt.PriorityTreeQueue;
import com.github.gumtreediff.tree.Tree;
import org.refactoringminer.astDiff.utils.TreeUtilFunctions;

import java.util.*;
import java.util.function.Function;

/* Created by pourya on 2023-06-14 2:10 p.m. */
public class MissingIdenticalSubtree extends GreedySubtreeMatcher implements TreeMatcher {
    private static final int DEFAULT_MIN_PRIORITY = 1;
    protected int minPriority = DEFAULT_MIN_PRIORITY;

    private static final String DEFAULT_PRIORITY_CALCULATOR = "height";
    protected Function<Tree, Integer> priorityCalculator = PriorityTreeQueue
            .getPriorityCalculator(DEFAULT_PRIORITY_CALCULATOR);

    protected Tree src;
    protected Tree dst;
    protected ExtendedMultiMappingStore mappings;


    @Override
    public void match(Tree src, Tree dst, ExtendedMultiMappingStore mappingStore) {
        this.src = src;
        this.dst = dst;
        this.mappings = mappingStore;

        var multiMappings = new MultiMappingStore();
        PriorityTreeQueue srcTrees = new DefaultPriorityTreeQueue(src, this.minPriority, this.priorityCalculator);
        PriorityTreeQueue dstTrees = new DefaultPriorityTreeQueue(dst, this.minPriority, this.priorityCalculator);

        while (!(srcTrees.isEmpty() || dstTrees.isEmpty())) {
            PriorityTreeQueue.synchronize(srcTrees, dstTrees);
            if (srcTrees.isEmpty() || dstTrees.isEmpty())
                break;

            var currentPrioritySrcTrees = srcTrees.pop();
            var currentPriorityDstTrees = dstTrees.pop();

            for (var currentSrc : currentPrioritySrcTrees)
                for (var currentDst : currentPriorityDstTrees)
                    if (currentSrc.getMetrics().hash == currentDst.getMetrics().hash)
                        if (TreeUtilFunctions.isIsomorphicTo(currentSrc, currentDst)) {
                            if (!mappingStore.isSrcMapped(currentSrc) && !mappingStore.isDstMapped(currentDst))
                                multiMappings.addMapping(currentSrc, currentDst);
                        }

            for (var t : currentPrioritySrcTrees)
                if (!multiMappings.hasSrc(t))
                    srcTrees.open(t);
            for (var t : currentPriorityDstTrees)
                if (!multiMappings.hasDst(t))
                    dstTrees.open(t);
        }

        filterMappings(multiMappings);
    }
    @Override
    public void filterMappings(MultiMappingStore multiMappings) {
        List<Mapping> ambiguousList = new ArrayList<>();
        Set<Tree> ignored = new HashSet<>();
        for (var src : multiMappings.allMappedSrcs()) {
            var isMappingUnique = false;
            if (tinyTrees(src,multiMappings,minPriority))
                continue;
            if (multiMappings.isSrcUnique(src)) {
                var dst = multiMappings.getDsts(src).stream().findAny().get();
                if (multiMappings.isDstUnique(dst)) {
                    if (isAcceptable(src,dst))
                        mappings.addMappingRecursively(src,dst);
                    isMappingUnique = true;
                }

            }
            if (!tinyTrees(src,multiMappings,minPriority) && !(ignored.contains(src) || isMappingUnique))
            {
                var adsts = multiMappings.getDsts(src);
                var asrcs = multiMappings.getSrcs(multiMappings.getDsts(src).iterator().next());
                for (Tree asrc : asrcs)
                    for (Tree adst : adsts) {
                        ambiguousList.add(new Mapping(asrc, adst));
                    }
                ignored.addAll(asrcs);
            }
            Set<Tree> srcIgnored = new HashSet<>();
            Set<Tree> dstIgnored = new HashSet<>();
            Collections.sort(ambiguousList, new MappingComparators.FullMappingComparator(mappings.getMonoMappingStore()));
            // Select the best ambiguous mappings
            retainBestMapping(ambiguousList, srcIgnored, dstIgnored);
        }
    }

    private boolean isAcceptable(Tree src, Tree dst) {
        //FIXME: Inside the condition (if-for-while) ignore it
        if (TreeUtilFunctions.isStatement(src.getType().name) && !src.getType().name.equals(Constants.BLOCK))
            if (src.getType().name.equals(Constants.RETURN_STATEMENT) && src.getMetrics().height <= 2)
                return false;
            else
                return true;
        else if (src.getType().name.equals(Constants.JAVA_DOC))
            return true;
        else if (src.getType().name.equals(Constants.METHOD_INVOCATION))
            return true;
        else if (src.getType().name.equals(Constants.METHOD_INVOCATION_ARGUMENTS))
            return true;
        else if (src.getType().name.equals(Constants.METHOD_INVOCATION_RECEIVER))
            return true;
        else if (src.getType().name.equals(Constants.INFIX_EXPRESSION))
            return true;
        else if (src.getType().name.equals(Constants.CLASS_INSTANCE_CREATION))
            return true;
        else{
            return false;
        }
    }

    private static boolean tinyTrees(Tree src, MultiMappingStore multiMappings, int minP) {
        if (src.getMetrics().height <= minP){
            if (src.getType().name.equals(Constants.METHOD_INVOCATION_RECEIVER))
                return true;
            if (src.getType().name.equals(Constants.METHOD_INVOCATION_ARGUMENTS))
                return true;
            if (src.getType().name.equals(Constants.SIMPLE_TYPE ))
                return true;
        }
        if (src.getType().name.equals(Constants.METHOD_INVOCATION_RECEIVER)) {
            return true;
        }
        return false;
    }

    @Override
    protected void retainBestMapping(List<Mapping> mappingList, Set<Tree> srcIgnored, Set<Tree> dstIgnored) {
        List<Mapping> verifiedList = new ArrayList<>();
        for (Mapping mapping : mappingList) {
            if (isAcceptable(mapping.first, mapping.second))
                verifiedList.add(mapping);
        }
        while (verifiedList.size() > 0) {
            var mapping = verifiedList.remove(0);
            if (!(srcIgnored.contains(mapping.first) || dstIgnored.contains(mapping.second)))
            {
                mappings.addMappingRecursively(mapping.first, mapping.second);
                srcIgnored.add(mapping.first);
                srcIgnored.addAll(mapping.first.getDescendants());
                dstIgnored.add(mapping.second);
                dstIgnored.addAll(mapping.second.getDescendants());
            }
        }
    }
}
