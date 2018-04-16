import DatasetAuthorComponent from 'wherehows-web/components/dataset-author';

export default class DatasetsOwnersSuggestedOwnerCard extends DatasetAuthorComponent {
  /**
   * Sets the html tag binded to the element generated by this component. Using here to override
   * original set tagName in the extended DatasetAuthorComponent.
   */
  tagName = 'div';

  /**
   * Sets the class names binded to the html element generated by this component
   */
  classNames = ['dataset-authors-suggested__card'];

  /**
   * Sets the class names that are triggered by certain properties on the component evaluating truthy.
   * Using here to override the original set bindings in the extended DatasetAuthorComponent
   */
  classNameBindings = [];
}
