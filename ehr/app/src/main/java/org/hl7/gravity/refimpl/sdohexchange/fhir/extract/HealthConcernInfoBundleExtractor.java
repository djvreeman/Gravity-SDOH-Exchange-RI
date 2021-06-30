package org.hl7.gravity.refimpl.sdohexchange.fhir.extract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Condition.ConditionEvidenceComponent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.gravity.refimpl.sdohexchange.fhir.extract.HealthConcernInfoBundleExtractor.HealthConcernInfoHolder;
import org.hl7.gravity.refimpl.sdohexchange.util.FhirUtil;

public class HealthConcernInfoBundleExtractor extends BundleExtractor<List<HealthConcernInfoHolder>> {

  @Override
  public List<HealthConcernInfoHolder> extract(Bundle bundle) {
    Map<String, Observation> allObservations = FhirUtil.getFromBundle(bundle, Observation.class)
        .stream()
        .collect(Collectors.toMap(observation -> observation.getIdElement()
            .getIdPart(), Function.identity()));
    Map<String, QuestionnaireResponse> allQuestionnaireResponses =
        FhirUtil.getFromBundle(bundle, QuestionnaireResponse.class)
            .stream()
            .collect(Collectors.toMap(qr -> qr.getIdElement()
                .getIdPart(), Function.identity()));

    return FhirUtil.getFromBundle(bundle, Condition.class)
        .stream()
        .map(condition -> {
          Reference evidenceReference = condition.getEvidence()
              .stream()
              .map(ConditionEvidenceComponent::getDetail)
              .flatMap(Collection::stream)
              .findFirst()
              .orElse(null);

          QuestionnaireResponse questionnaireResponse = null;
          List<Observation> observations = new ArrayList<>();
          if (evidenceReference != null) {
            Observation evidenceObservation = allObservations.remove(evidenceReference.getReferenceElement()
                .getIdPart());
            observations.add(evidenceObservation);

            questionnaireResponse = evidenceObservation.getDerivedFrom()
                .stream()
                .filter(derivedFrom -> derivedFrom.getReferenceElement()
                    .getResourceType()
                    .equals(QuestionnaireResponse.class.getSimpleName()))
                .findFirst()
                .map(qrReference -> allQuestionnaireResponses.remove(qrReference.getReferenceElement()
                    .getIdPart()))
                .get();
            String questionnaireResponseId = questionnaireResponse.getIdElement()
                .getIdPart();
            Iterator<Entry<String, Observation>> iterator = allObservations.entrySet()
                .iterator();
            while (iterator.hasNext()) {
              Entry<String, Observation> observationEntry = iterator.next();
              Observation value = observationEntry.getValue();
              if (containsQuestionnaireReference(value, questionnaireResponseId)) {
                observations.add(value);
                iterator.remove();
              }
            }
          }
          return new HealthConcernInfoHolder(condition, questionnaireResponse, observations);
        })
        .collect(Collectors.toList());
  }

  private boolean containsQuestionnaireReference(Observation observation, String questionnaireId) {
    return observation.getDerivedFrom()
        .stream()
        .anyMatch(reference -> reference.getReferenceElement()
            .getResourceType()
            .equals(QuestionnaireResponse.class.getSimpleName()) && reference.getReferenceElement()
            .getIdPart()
            .equals(questionnaireId));
  }

  @Getter
  @AllArgsConstructor
  public static class HealthConcernInfoHolder {

    private final Condition condition;
    private final QuestionnaireResponse questionnaireResponse;
    private final List<Observation> observations;
  }
}