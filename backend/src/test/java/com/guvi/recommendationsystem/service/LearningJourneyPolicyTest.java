package com.guvi.recommendationsystem.service;
import com.guvi.recommendationsystem.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class LearningJourneyPolicyTest {
    @Test void sequenceUsesAvailableDifficultyMetadataWithoutInventingPrerequisites(){
        assertThat(List.of(ItemDifficulty.BEGINNER,ItemDifficulty.ALL_LEVELS,ItemDifficulty.INTERMEDIATE,ItemDifficulty.ADVANCED).stream().map(LearningJourneyService::difficultyOrder)).containsExactly(0,1,2,3);
        assertThat(LearningJourneyService.difficultyOrder(null)).isEqualTo(3);
        assertThat(LearningJourneyService.difficultyOrder(ItemDifficulty.UNKNOWN)).isEqualTo(4);
    }
    @Test void learningGoalPrioritizesInstructionalTypesWhileExploreKeepsRankingOrder(){
        var guide=new CatalogItem(1,1,"Guide","",ItemType.GUIDE,"https://example.test/",null,true,null,null);
        var tool=new CatalogItem(2,1,"Tool","",ItemType.TOOL,"https://example.test/",null,true,null,null);
        assertThat(LearningJourneyService.goalPriority(guide,OnboardingPurpose.LEARN)).isLessThan(LearningJourneyService.goalPriority(tool,OnboardingPurpose.LEARN));
        assertThat(LearningJourneyService.goalPriority(guide,OnboardingPurpose.EXPLORE)).isEqualTo(LearningJourneyService.goalPriority(tool,OnboardingPurpose.EXPLORE));
    }
    @Test void learningGoalTreatsArticlesLikeOtherInstructionalReadings(){
        var article=new CatalogItem(1,1,"Article","",ItemType.ARTICLE,"https://example.test/",null,true,null,null);
        var guide=new CatalogItem(2,1,"Guide","",ItemType.GUIDE,"https://example.test/",null,true,null,null);
        assertThat(LearningJourneyService.goalPriority(article,OnboardingPurpose.LEARN)).isZero().isEqualTo(LearningJourneyService.goalPriority(guide,OnboardingPurpose.LEARN));
        assertThat(LearningJourneyService.goalPriority(article,OnboardingPurpose.EXPLORE)).isZero();
    }
}
