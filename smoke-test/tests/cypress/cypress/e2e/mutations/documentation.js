const test_id = Math.floor(Math.random() * 100000);
const documentation_edited = `This is test${test_id} documentation EDITED`;
const wrong_url = "https://www.linkedincom";
const correct_url = "https://www.linkedin.com";

describe("add, remove documentation and link to dataset", () => {

    it("open test dataset page, edit and remove dataset documentation", () => {
        //edit documentation, and propose, decline proposal
        cy.loginWithCredentials();
        cy.visit("/dataset/urn:li:dataset:(urn:li:dataPlatform:hive,SampleCypressHiveDataset,PROD)/Schema");
        cy.get("[role='tab']").contains("Documentation").click();
        cy.waitTextVisible("my hive dataset");
        cy.waitTextVisible("Sample doc");
        cy.clickOptionWithText("Edit");
        cy.focused().clear();
        cy.focused().type(documentation_edited);
        cy.get("button").contains("Propose").click();
        cy.waitTextVisible("Proposed description update!").wait(3000);
        //cy.waitTextVisible("No documentation yet");
        cy.clickOptionWithText("Inbox");
        cy.waitTextVisible("Update Description Proposal");
        cy.contains("View difference").first().click();
        cy.waitTextVisible(documentation_edited);
        cy.get("[data-icon='close'").click();
        cy.get("button").contains("Decline").click();
        cy.waitTextVisible("Are you sure you want to reject this proposal?");
        cy.get("button").contains("Yes").click();
        cy.waitTextVisible("Proposal declined.");
        cy.visit("/dataset/urn:li:dataset:(urn:li:dataPlatform:hive,SampleCypressHiveDataset,PROD)/Schema");
        cy.get("[role='tab']").contains("Documentation").click();
        cy.waitTextVisible("my hive dataset");
        //edit documentation, and propose, approve proposal
        cy.clickOptionWithText("Edit");
        cy.focused().clear().wait(1000);
        cy.focused().type(documentation_edited);
        cy.get("button").contains("Propose").click();
        cy.waitTextVisible("Proposed description update!").wait(3000);
        cy.waitTextVisible("my hive dataset");
        cy.clickOptionWithText("Inbox");
        cy.waitTextVisible("Update Description Proposal");
        cy.get("button").contains("Approve").click();
        cy.waitTextVisible("Are you sure you want to accept this proposal?");
        cy.get("button").contains("Yes").click();
        cy.waitTextVisible("Successfully accepted the proposal!");
        cy.visit("/dataset/urn:li:dataset:(urn:li:dataPlatform:hive,SampleCypressHiveDataset,PROD)/Schema");
        cy.get("[role='tab']").contains("Documentation").click();
        cy.ensureTextNotPresent("my hive dataset");
        cy.waitTextVisible(documentation_edited);
        cy.ensureTextNotPresent("Add Documentation");
        //return documentation to original state
        cy.clickOptionWithText("Edit");
        cy.focused().clear().wait(1000);
        cy.focused().type("my hive dataset");
        cy.get("button").contains("Save").click();
        cy.waitTextVisible("Description Updated");
        cy.waitTextVisible("my hive dataset");
    });

    it("open test dataset page, remove and add dataset link", () => {
        cy.loginWithCredentials();
        cy.visit("/dataset/urn:li:dataset:(urn:li:dataPlatform:hive,SampleCypressHiveDataset,PROD)/Schema");
        cy.get("[role='tab']").contains("Documentation").click();
        cy.contains("Sample doc").trigger("mouseover", { force: true });
        cy.get('[data-icon="delete"]').click();
        cy.waitTextVisible("Link Removed");
        cy.get("button").contains("Add Link").click();
        cy.get("#addLinkForm_url").type(wrong_url);
        cy.waitTextVisible("This field must be a valid url.");
        cy.focused().clear();
        cy.waitTextVisible("A URL is required.");
        cy.focused().type(correct_url);
        cy.ensureTextNotPresent("This field must be a valid url.");
        cy.get("#addLinkForm_label").type("Sample doc");
        cy.get('[role="dialog"] button').contains("Add").click();
        cy.waitTextVisible("Link Added");
        cy.get("[role='tab']").contains("Documentation").click();
        cy.get(`[href='${correct_url}']`).should("be.visible");
    });
});