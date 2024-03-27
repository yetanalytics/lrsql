# General Questions

### Is SQL LRS a Learning Record Provider (LRP), Learning Management System (LMS), or content store?

No, SQL LRS is strictly a [Learning Record Store](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-About.md#def-learning-record-store) (LRS), and is not an application that includes its own LRP or LMS. Unlike a [Learning Record Provider](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-About.md#def-learning-record-provider) (LRP), SQL LRS does not produce learning data on its own, and unlike a [Learning Management System](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-About.md#def-learning-management-system) (LMS), it does not host, distribute, author, or serve as a repository for learning content.

As an LRS, SQL LRS is strictly a database application for xAPI statements, attachments, and documents. It receives, validates, stores, and makes available xAPI data produced by external LRPs or LMSs.

### I've used Yet's Cloud LRS products in the past. What are the differences?

If you previously used Yet's Cloud LRS products, it is important to be aware of certain differences:

- Tenancy is not supported in SQL LRS; the entire database can be considered to be a single default tenant.
- All operations in SQL LRS are synchronous; async operations are not supported.
- `stored` timestamps are not strictly monotonic in SQL LRS; two or more Statements may be assigned the same timestamp if stored in quick succession.
- If a Statement voids a target Statement that is itself voiding, SQL LRS will accept it upon insertion, though it will not update the state of the target Statement as per the [xAPI spec](https://github.com/adlnet/xAPI-Spec/blob/master/xAPI-Communication.md#214-voided-statements). (The Cloud LRS, on the other hand, will simply reject the voiding Statement.)
