-- V10__seed_template_examples.sql
-- Insère deux templates d'exemple (un VALIDATION, un MAPPING) en état PUBLISHED
-- pour illustrer le fonctionnement du système de templates.

INSERT INTO validation_templates (name, description, content, status, version, parent_id, created_by, created_at, updated_at)
SELECT
    'Standard Order Validation',
    'Valide les champs obligatoires d''une commande : orderId, email et montant.',
    '{
       "rules": [
         { "field": "orderId", "ruleType": "NOT_NULL"     },
         { "field": "email",   "ruleType": "REGEX_EMAIL"  },
         { "field": "amount",  "ruleType": "TYPE_NUMBER"  },
         { "field": "phone",   "ruleType": "REGEX_PHONE"  }
       ]
     }'::jsonb,
    'PUBLISHED',
    1,
    NULL,
    u.id,
    NOW(),
    NOW()
FROM users u
WHERE u.username = 'admin'
LIMIT 1;

INSERT INTO mapping_templates (name, description, content, status, version, parent_id, created_by, created_at, updated_at)
SELECT
    'CRM → ERP Field Mapping',
    'Mappe les champs du payload CRM vers le schéma ERP cible.',
    '{
       "mappings": [
         { "source": "firstName", "target": "first_name",  "type": "DIRECT"      },
         { "source": "lastName",  "target": "last_name",   "type": "DIRECT"      },
         { "source": "birthDate", "target": "dob",         "type": "DATE_FORMAT",
           "expression": "yyyy-MM-dd" },
         { "source": "amount",    "target": "total_price", "type": "EXPRESSION",
           "expression": "amount * 1.20" }
       ]
     }'::jsonb,
    'PUBLISHED',
    1,
    NULL,
    u.id,
    NOW(),
    NOW()
FROM users u
WHERE u.username = 'admin'
LIMIT 1;