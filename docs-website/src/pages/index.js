import React from 'react';
import clsx from 'clsx';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl from '@docusaurus/useBaseUrl';
import ThemedImage from '@theme/ThemedImage';
import styles from './styles.module.css';

import Image from '@theme/IdealImage';
import CodeBlock from '@theme/CodeBlock';
import LogoLinkedin from './logos/linkedin.svg';
import LogoExpedia from './logos/expedia.svg';
import LogoSaxo from './logos/SaxoBank.svg';
import LogoGrofers from './logos/grofers.png';
import LogoTypeform from './logos/typeform.svg';
import LogoSpothero from './logos/SpotHero.png';
import LogoGeotab from './logos/geotab.jpg';
import LogoThoughtworks from './logos/Thoughtworks.png';
import LogoViasat from './logos/viasat.png';

const features = [
  {
    title: 'Open Source',
    imageUrl: 'img/undraw_open_source_1qxw.svg',
    description: (
      <>
        DataHub was originally <Link to={"https://engineering.linkedin.com/blog/2019/data-hub"}>built
        at LinkedIn</Link> and subsequently <Link to={"https://github.com/linkedin/datahub"}>open-sourced</Link> under
        the Apache 2.0 License. It now has a thriving community with over 75 contributors.
      </>
    ),
  },
  {
    title: 'Forward Looking Architecture',
    imageUrl: 'img/undraw_building_blocks_n0nc.svg',
    description: (
      <>
        DataHub follows a <Link to={"https://engineering.linkedin.com/blog/2020/datahub-popular-metadata-architectures-explained"}>push-based architecture</Link>,
        which means it's built for continuously changing metadata. The modular design lets it scale with data growth at any organization.
      </>
    ),
  },
  {
    title: 'Massive Ecosystem',
    imageUrl: 'img/undraw_online_connection_6778.svg',
    description: (
      // TODO: update the integrations link to scroll down the page.
      <>
        DataHub has pre-built integrations with Kafka, MySQL, MS SQL, Postgres, LDAP, Snowflake,
        Hive, BigQuery, and <Link to={"docs/metadata-ingestion"}>many others</Link>.
      </>
    ),
  },
];

const svgFormatter = (Logo) => {
  return <Logo width="100%" height="100%" />;
}
const pngFormatter = (src) => {
  return <Image img={src} />
}

const logos = [
  {
    name: 'LinkedIn',
    image: svgFormatter(LogoLinkedin),
  },
  {
    name: 'Expedia Group',
    image: svgFormatter(LogoExpedia),
  },
  {
    name: 'Saxo Bank',
    image: svgFormatter(LogoSaxo),
  },
  {
    name: 'Grofers',
    image: pngFormatter(LogoGrofers),
  },
  {
    name: 'Typeform',
    image: svgFormatter(LogoTypeform),
  },
  {
    name: 'SpotHero',
    image: pngFormatter(LogoSpothero),
  },
  {
    name: 'Geotab',
    image: pngFormatter(LogoGeotab),
  },
  {
    name: 'ThoughtWorks',
    image: pngFormatter(LogoThoughtworks),
  },
  {
    name: 'Viasat',
    image: pngFormatter(LogoViasat),
  },
];

const example_recipe = `
source:
  type: "mysql"
  config:
    username: "datahub"
    password: "datahub"
    host_port: "localhost:3306"
sink:
  type: "datahub-rest"
  config:
    server: 'http://localhost:8080'`.trim();
const example_recipe_run = "datahub ingest -c recipe.yml"

function Feature({ imageUrl, title, description }) {
  const imgUrl = useBaseUrl(imageUrl);
  return (
    <div className={clsx('col col--4', styles.feature)}>
      {imgUrl && (
        <div className="text--center">
          <img className={styles.featureImage} src={imgUrl} alt={title} />
        </div>
      )}
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}

function Home() {
  const context = useDocusaurusContext();
  const { siteConfig = {} } = context;
  return (
    <Layout
      title={`${siteConfig.title} - ${siteConfig.tagline}`}
      description="Description will go into a meta tag in <head />">
      <header className={clsx('hero', styles.heroBanner)}>
        <div className="container">
          <div className="row">
            <div className="col col--8">
              <h1 className={clsx("hero__title", styles.not_bold_text, styles.centerTextMobile)}>{siteConfig.tagline}</h1>
              <p className={clsx("hero__subtitle", styles.centerTextMobile)}>TODO: a brief description of what datahub is and why it might be interesting to a company. Can be a few lines</p>
              <div className={styles.buttons}>
                <Link
                  className={clsx(
                    'button button--primary button--lg', styles.hero_button
                  )}
                  to={useBaseUrl('docs/')}>
                  Get Started
            </Link>
                <Link
                  className={clsx(
                    'button button--secondary button--outline button--lg',
                    styles.hero_button
                  )}
                  to='https://join.slack.com/t/datahubspace/shared_invite/zt-dkzbxfck-dzNl96vBzB06pJpbRwP6RA'>
                  Join our Slack
            </Link>
              </div>
            </div>
            <div className={clsx("col col--4", styles.hiddenMobile, styles.bumpUpLogo)}>
              <ThemedImage
                alt="DataHub Logo"
                sources={{
                  light: useBaseUrl(siteConfig.themeConfig.navbar.logo.src),
                  dark: useBaseUrl(siteConfig.themeConfig.navbar.logo.srcDark),
                }}
              />
            </div>
          </div>
        </div>
      </header>
      <section className={styles.big_padding_bottom}>
        {features && features.length > 0 && (
          <div className={styles.features}>
            <div className="container">
              <div className="row">
                {features.map((props, idx) => (
                  <Feature key={idx} {...props} />
                ))}
              </div>
            </div>
          </div>
        )}
      </section>
      <section className={styles.section}>
        <div className="container">
          <h1 className={clsx(styles.centerText)}>
            <span className={styles.larger_on_desktop}>
              Trusted Across the Industry
            </span>
          </h1>
          <div className="row">
            {logos.map((logo) => (
              <div key={logo.name} className="col col--3">
                {logo.image}
              </div>
            ))}
          </div>
        </div>
      </section>
      <section className={styles.section}>
        <div className="container">
          <h1 className={clsx(styles.centerText, styles.big_padding_bottom)}>
            <span className={styles.larger_on_desktop}>
              How does it work?
            </span>
          </h1>
          <div className="row">
            <div className="col col--6">
              <h2><span className={styles.larger_on_desktop}>
                1. Automated Metadata Ingestion
              </span></h2>
              <p>
                There're two way to get metadata into DataHub: <b>push</b> and <b>pull</b>.
                Notice that DataHub's push-based architecture also supports
                pull, but pull-first systems cannot support push.
              </p>
              <p>
                <b>Push</b>-based ingestion can use a prebuilt emitter or can emit custom events using our framework.
              </p>
              <p>
                <b>Pull</b>-based ingestion crawls a metadata source. We have prebuilt integrations with
                Kafka, MySQL, MS SQL, Postgres, LDAP, Snowflake, Hive, BigQuery, and more.
                Ingestion can be automated using our Airflow integration or another scheduler of choice.
                {/* TODO: add logos for these integration */}
              </p>
              <p>
                Learn more about metadata ingestion with DataHub in the <Link to={'docs/metadata-ingestion'}>docs</Link>.
              </p>
            </div>
            <div className="col col--6">
              <p className={styles.small_padding_top}>
              <CodeBlock className={'language-yml'} metastring='title="recipe.yml"'>{example_recipe}</CodeBlock>
              </p>
              <p>
              <CodeBlock className={'language-shell'}>{example_recipe_run}</CodeBlock>
              </p>
            </div>
          </div>
        </div>
      </section>
    </Layout>
  );
}

export default Home;
