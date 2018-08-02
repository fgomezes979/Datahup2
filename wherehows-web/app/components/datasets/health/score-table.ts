import Component from '@ember/component';
import { IHealthScore } from 'wherehows-web/typings/api/datasets/health';
import { computed, setProperties, getProperties } from '@ember/object';
import ComputedProperty from '@ember/object/computed';

/**
 * Adds properties specifically to help the table render each row to the basic health score
 */
interface IRenderedHealthScore extends IHealthScore {
  highlightClass: string;
  isHidden: boolean;
}

export default class DatasetsHealthScoreTable extends Component {
  /**
   * Sets the class names binded to the html element generated by this component
   * @type {Array<string>}
   */
  classNames = ['nacho-table', 'dataset-health__score-table'];

  /**
   * Sets the tag to be rendered for the html element generated by this component
   * @type {string}
   */
  tagName = 'table';

  /**
   * Expected headers for the detailed table
   * @type {Array<string>}
   */
  headers = ['Category', 'Description', 'Score', 'Severity'];

  /**
   * Classes for each header element based on the actual header column title
   * @type {Array<string>}
   */
  headerClasses = this.headers.map(header => `dataset-health__score-table__${header.toLowerCase()}`);

  /**
   * Passed in table data, mostly raw detailed information about the compliance score details and the
   * breakdown of such.
   * @type {Array<IHealthScore>}
   */
  tableData: Array<IHealthScore>;

  /**
   * Passed in severity filter. This property lives on the dataset-health container component and its
   * modification is triggered by a click in the graphing component
   * @type {string}
   */
  currentSeverityFilter: string;

  /**
   * Passed in category filter. This property lives on the dataset-health container component and its
   * modification is triggered by a click in the graphing component
   * @type {string}
   */
  currentCategoryFilter: string;

  /**
   * Calculates table data from the passed in information by appending each row with information that helps to
   * style the table.
   * @type {ComputedProperty<Array<IRenderedHealthScore>>}
   */
  renderedTableData: ComputedProperty<Array<IRenderedHealthScore>> = computed(
    'tableData',
    'currentCategoryFilter',
    'currentSeverityFilter',
    function(this: DatasetsHealthScoreTable): Array<IRenderedHealthScore> {
      const { tableData, currentCategoryFilter: categoryFilter, currentSeverityFilter: severityFilter } = getProperties(
        this,
        'tableData',
        'currentCategoryFilter',
        'currentSeverityFilter'
      );
      return tableData.map(healthScore => ({
        ...healthScore,
        isHidden:
          (!!categoryFilter && healthScore.category !== categoryFilter) ||
          (!!severityFilter && healthScore.severity !== severityFilter),
        highlightClass: `dataset-health__score-table__row--${(healthScore.severity || 'normal').toLowerCase()}`
      }));
    }
  );

  constructor() {
    super(...arguments);

    setProperties(this, {
      tableData: this.tableData || [],
      currentSeverityFilter: this.currentSeverityFilter || '',
      currentCategoryFilter: this.currentCategoryFilter || ''
    });
  }
}
