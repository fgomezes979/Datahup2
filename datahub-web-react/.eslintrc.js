module.exports = {
    parser: '@typescript-eslint/parser', // Specifies the ESLint parser
    extends: [
        'airbnb',
        'airbnb-typescript',
        'airbnb/hooks',
        'plugin:@typescript-eslint/recommended',
        'plugin:jest/recommended',
        'prettier',
    ],
    plugins: ['@typescript-eslint'],
    parserOptions: {
        ecmaVersion: 2020, // Allows for the parsing of modern ECMAScript features
        sourceType: 'module', // Allows for the use of imports
        ecmaFeatures: {
            jsx: true, // Allows for the parsing of JSX
        },
        project: './tsconfig.json',
    },
    rules: {
        '@typescript-eslint/no-explicit-any': 'warn',
        'arrow-body-style': 'warn',
        'class-methods-use-this': 'off',
        'import/no-extraneous-dependencies': 'off',
        'import/prefer-default-export': 'off', // TODO: remove this lint rule
        'no-console': 'off',
        'no-plusplus': 'off',
        'no-prototype-builtins': 'warn',
        'no-restricted-exports': 'warn',
        'no-underscore-dangle': 'off',
        'no-unsafe-optional-chaining': 'off',
        'prefer-exponentiation-operator': 'off',
        'prefer-regex-literals': 'warn',
        'react/destructuring-assignment': 'off',
        'react/function-component-definition': 'off',
        'react/jsx-no-bind': 'off',
        'react/jsx-no-constructed-context-values': 'off',
        'react/jsx-no-useless-fragment': 'warn',
        'react/jsx-props-no-spreading': 'off',
        'react/no-unstable-nested-components': 'off',
        'react/require-default-props': 'off',
        '@typescript-eslint/no-unused-vars': [
            'error',
            {
                varsIgnorePattern: '^_',
                argsIgnorePattern: '^_',
            },
        ],
    },
    settings: {
        react: {
            version: 'detect', // Tells eslint-plugin-react to automatically detect the version of React to use
        },
    },
};
