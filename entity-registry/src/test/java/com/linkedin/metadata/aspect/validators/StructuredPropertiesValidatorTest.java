package com.linkedin.metadata.aspect.validators;

import com.linkedin.common.urn.Urn;
import com.linkedin.entity.Aspect;
import com.linkedin.metadata.aspect.plugins.validation.AspectRetriever;
import com.linkedin.metadata.aspect.plugins.validation.AspectValidationException;
import com.linkedin.metadata.aspect.validation.StructuredPropertiesValidator;
import com.linkedin.metadata.models.registry.EntityRegistry;
import com.linkedin.structured.PrimitivePropertyValue;
import com.linkedin.structured.PrimitivePropertyValueArray;
import com.linkedin.structured.StructuredProperties;
import com.linkedin.structured.StructuredPropertyDefinition;
import com.linkedin.structured.StructuredPropertyValueAssignment;
import com.linkedin.structured.StructuredPropertyValueAssignmentArray;
import java.net.URISyntaxException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StructuredPropertiesValidatorTest {

  static class MockAspectRetriever implements AspectRetriever {
    StructuredPropertyDefinition _propertyDefinition;

    MockAspectRetriever(StructuredPropertyDefinition defToReturn) {
      this._propertyDefinition = defToReturn;
    }

    @Nullable
    @Override
    public Aspect getLatestAspectObject(@Nonnull Urn urn, @Nonnull String aspectName) {
      return new Aspect(_propertyDefinition.data());
    }

    @Nonnull
    @Override
    public EntityRegistry getEntityRegistry() {
      return null;
    }
  }

  @Test
  public void testValidateAspectUpsert() throws URISyntaxException {
    StructuredPropertyDefinition numberPropertyDef =
        new StructuredPropertyDefinition()
            .setValueType(Urn.createFromString("urn:li:type:datahub.number"));
    StructuredPropertyValueAssignment assignment =
        new StructuredPropertyValueAssignment()
            .setPropertyUrn(
                Urn.createFromString("urn:li:structuredProperty:io.acryl.privacy.retentionTime"))
            .setValues(new PrimitivePropertyValueArray(PrimitivePropertyValue.create(30.0)));
    StructuredProperties numberPayload =
        new StructuredProperties()
            .setProperties(new StructuredPropertyValueAssignmentArray(assignment));

    try {
      boolean isValid =
          StructuredPropertiesValidator.validate(
              numberPayload, new MockAspectRetriever(numberPropertyDef));
      Assert.assertTrue(isValid);
    } catch (AspectValidationException e) {
      throw new RuntimeException(e);
    }

    // Assign string value to number property
    StructuredPropertyValueAssignment stringAssignment =
        new StructuredPropertyValueAssignment()
            .setPropertyUrn(
                Urn.createFromString("urn:li:structuredProperty:io.acryl.privacy.retentionTime"))
            .setValues(new PrimitivePropertyValueArray(PrimitivePropertyValue.create("hello")));
    StructuredProperties stringPayload =
        new StructuredProperties()
            .setProperties(new StructuredPropertyValueAssignmentArray(stringAssignment));
    try {
      StructuredPropertiesValidator.validate(
          stringPayload, new MockAspectRetriever(numberPropertyDef));
      Assert.fail("Should have raised exception for mis-matched types");
    } catch (AspectValidationException e) {
      Assert.assertTrue(e.getMessage().contains("should be a number"));
    }

    // Assign invalid date
    StructuredPropertyDefinition datePropertyDef =
        new StructuredPropertyDefinition()
            .setValueType(Urn.createFromString("urn:li:type:datahub.date"));
    try {
      StructuredPropertiesValidator.validate(
          stringPayload, new MockAspectRetriever(datePropertyDef));
      Assert.fail("Should have raised exception for mis-matched types");
    } catch (AspectValidationException e) {
      Assert.assertTrue(e.getMessage().contains("should be a date with format"));
    }

    // Assign valid date
    StructuredPropertyValueAssignment dateAssignment =
        new StructuredPropertyValueAssignment()
            .setPropertyUrn(
                Urn.createFromString("urn:li:structuredProperty:io.acryl.privacy.retentionTime"))
            .setValues(
                new PrimitivePropertyValueArray(PrimitivePropertyValue.create("2023-10-24")));
    StructuredProperties datePayload =
        new StructuredProperties()
            .setProperties(new StructuredPropertyValueAssignmentArray(dateAssignment));
    try {
      boolean isValid =
          StructuredPropertiesValidator.validate(
              datePayload, new MockAspectRetriever(datePropertyDef));
      Assert.assertTrue(isValid);
    } catch (AspectValidationException e) {
      throw new RuntimeException(e);
    }

    StructuredPropertyDefinition stringPropertyDef =
        new StructuredPropertyDefinition()
            .setValueType(Urn.createFromString("urn:li:type:datahub.string"));
    // Valid strings (both the date value and "hello" are valid)
    try {
      boolean isValid =
          StructuredPropertiesValidator.validate(
              stringPayload, new MockAspectRetriever(stringPropertyDef));
      Assert.assertTrue(isValid);
      isValid =
          StructuredPropertiesValidator.validate(
              datePayload, new MockAspectRetriever(stringPropertyDef));
      Assert.assertTrue(isValid);
    } catch (AspectValidationException e) {
      throw new RuntimeException(e);
    }

    // Invalid: assign a number to the string property
    try {
      StructuredPropertiesValidator.validate(
          numberPayload, new MockAspectRetriever(stringPropertyDef));
      Assert.fail("Should have raised exception for mis-matched types");
    } catch (AspectValidationException e) {
      Assert.assertTrue(e.getMessage().contains("should be a string"));
    }
  }
}
