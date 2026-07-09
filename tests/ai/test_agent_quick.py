"""Quick smoke test for RecoveryAgent (run: python3 tests/ai/test_agent_quick.py)"""
import sys, json
sys.path.insert(0, 'ai-service')
from app.models.schemas import ChatRequest
from app.agents.recovery_agent import RecoveryAgent

class FakeLLM:
    def chat(self,messages,system,temperature=0.7,max_tokens=512): return 'Test reply'
    def analyze_sentiment(self,text): return {'emotion':'neutral','intensity':0,'crisis':False}
    def generate_personality(self,style='balanced'): return 'system prompt'

agent = RecoveryAgent(llm=FakeLLM())

r = agent.chat(ChatRequest(message='Hi', user_id='t1'))
assert r.reply and not r.crisis; print('1 hello OK')

r = agent.chat(ChatRequest(message='хочу употребить', user_id='t1'))
assert r.crisis; print('2 crisis OK')

ctx = {'diary':[{'date':'2026-07-08','intensity':7,'trigger':'fight'}],'progress':{'days':45,'medal':'Oran'},'steps':3}
r = agent.chat(ChatRequest(message='bad', user_id='t1', context=ctx))
assert r.reply; print('3 context OK')

agent.update_profile('t1',{'ai_name':'Mentor','style':'coach'})
p = agent.get_profile('t1')
assert p.get('ai_name') == 'Mentor'; print('4 profile OK')

agent.memory.add_fact('t1',{'type':'fact','content':'test','importance':1})
assert len(agent.get_memory('t1')) > 0; print('5 memory OK')

print('\nAll 5 tests passed')
