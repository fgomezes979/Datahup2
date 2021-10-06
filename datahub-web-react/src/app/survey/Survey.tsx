import React, { useState, useEffect } from 'react';
import styled from 'styled-components';
import { message } from 'antd';
import ArrowsIcon from '../../images/arrows-to-open.svg';
import CloseIcon from '../../images/close-button.svg';
import { EmojiQuestion } from './EmojiQuestion';
import { YesNoQuestion } from './YesNoQuestion';
import { FreeTextQuestion } from './FreeTextQuestion';

const ShowSurveyButton = styled.button<{ showModal: boolean }>`
    position: fixed;
    bottom: 0em;
    right: 10vw;
    background: #22323d;
    border: none;
    height: 2.5em;
    width: 10em;
    border-radius: 10px 10px 0 0;
    color: white;
    display: ${(props) => (props.showModal ? 'none' : 'block')};
`;

const CloseButton = styled.button`
    background: none;
    position: absolute;
    top: 0.75em;
    right: 0.5em;
    border: none;
`;

const Text = styled.p`
    color: white;
    font-weight: bold;
    font-size: large;
    text-align: center;
    margin: auto;
`;

const QuestionText = styled(Text)`
    margin-bottom: 0.3em;
`;

const ModalBackground = styled.div<{ showModal: boolean }>`
    z-index: auto;
    display: ${(props) => (props.showModal ? 'block' : 'none')};
    position: fixed;
    top: 0;
    left: 0;
    height: 100vh;
    width: 100vw;
    background: rgba(0, 0, 0, 0.5);
`;

const Modal = styled.div`
    position: fixed;
    bottom: 0em;
    right: 10vw;
    background: #22323d;
    max-width: 25em;
    min-width: 15em;
    width: 33%;
    height: 15em;
    border-top-right-radius: 10px;
    border-top-left-radius: 10px;
    padding: 0.75rem;
`;

const FlexRow = styled.div`
    display: flex;
    flex-direction: row;
    justify-content: space-evenly;
    margin: 0px;
`;

const FlexEndRow = styled(FlexRow)`
    justify-content: flex-end;
`;

const FlexStartRow = styled(FlexRow)`
    justify-content: flex-start;
`;

const FlexContainer = styled.div`
    display: flex;
    flex-direction: column;
    height: 13em;
    margin-right: 1em;
    margin-left: 1em;
    justify-content: center;
`;

export const Survey = () => {
    const [showModal, setShowModal] = useState(false);
    const [response1, setResponse1] = useState('');
    const [response2, setResponse2] = useState('');
    const [response3, setResponse3] = useState('');
    const [showQuestionNumber, setShowQuestionNumber] = useState(0);
    useEffect(() => {
        setShowQuestionNumber((s) => s + 1);
    }, [response1, response2]);

    const submitResponses = (e) => {
        e.preventDefault();
        setShowModal(false);
        console.log(response3);
        const requestOptions = {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ response: response3 }),
        };
        fetch('/entities?action=ingest', requestOptions)
            .then(async (response) => {
                console.log(response);
                if (!response.ok) {
                    const data = await response.json();
                    console.log('This is the response data ', data);
                    const error = (data && data.message) || response.status;
                    console.log('error: ', error);
                    return Promise.reject(error);
                }
                return Promise.resolve();
            })
            .catch((error) => {
                message.error(`Failed to submit response! ${error}`);
            });
    };

    const showSurveyModal = () => {
        setShowModal(true);
    };

    const closeSurveyModal = () => {
        setShowModal(false);
    };

    return (
        <>
            <ModalBackground showModal={showModal}>
                <Modal>
                    <FlexRow>
                        <CloseButton type="button" onClick={() => closeSurveyModal()}>
                            <img src={CloseIcon} alt="close button icon" />
                        </CloseButton>
                    </FlexRow>
                    <FlexContainer>
                        <form onSubmit={(e) => submitResponses(e)}>
                            {showQuestionNumber === 1 && (
                                <EmojiQuestion
                                    setResponse1={setResponse1}
                                    FlexRow={FlexRow}
                                    QuestionText={QuestionText}
                                />
                            )}
                            {showQuestionNumber === 2 && (
                                <YesNoQuestion
                                    setResponse2={setResponse2}
                                    FlexRow={FlexRow}
                                    QuestionText={QuestionText}
                                    Text={Text}
                                />
                            )}
                            {showQuestionNumber === 3 && (
                                <FreeTextQuestion
                                    response3={response3}
                                    setResponse3={setResponse3}
                                    FlexRow={FlexRow}
                                    QuestionText={QuestionText}
                                    FlexEndRow={FlexEndRow}
                                    FlexStartRow={FlexStartRow}
                                />
                            )}
                        </form>
                    </FlexContainer>
                </Modal>
            </ModalBackground>
            <ShowSurveyButton type="button" showModal={showModal} onClick={() => showSurveyModal()}>
                <img src={ArrowsIcon} alt="arrow to open survey" />
            </ShowSurveyButton>
        </>
    );
};
