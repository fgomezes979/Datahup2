import Component from '@ember/component';
import { computed } from '@ember-decorators/object';
import { IFacetSelections } from 'wherehows-web/utils/api/search';

/**
 * Presentation component of a facet
 */
export default class SearchFacet extends Component {
  /**
   * Facet selections
   */
  selections: IFacetSelections;

  /**
   * Computed property to check if there is any selection in the
   * facet. If that is the case, a clear button will show up.
   */
  @computed('selections')
  get showClearBtn(): boolean {
    const selections = this.selections || {};
    return Object.keys(selections).reduce((willShowClearBtn: boolean, selectionKey: string) => {
      return willShowClearBtn || selections[selectionKey];
    }, false);
  }
}
